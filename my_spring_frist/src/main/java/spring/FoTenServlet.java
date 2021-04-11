package spring;


import spring.annitation.FoTenAutowire;
import spring.annitation.FoTenController;
import spring.annitation.FoTenRequestMapping;
import spring.annitation.FoTenService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;


/**
 * @author 蒋宇辰
 * @date 2021/4/9
 * @version 1.0.1
 */
public class FoTenServlet extends HttpServlet {

    /**
     * 保存用户配置好的配置文件
     */
    private Properties contextConfig = new Properties();

    /**
     * 缓存从包路径下扫描到的全类名
     */
    private List<String> classNames = new ArrayList<String>();

    /**
     * 保存所有扫描到类的实例
     */
    private Map<String,Object> ioc = new HashMap<String,Object>();

    /**
     * 保存URL 和 Method 的对应关系
     */
    private Map<String,Object> handlerMapping = new HashMap<String, Object>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

       try {
           doDispatch(req,resp);
       }catch (Exception e){
             e.printStackTrace();
           resp.getWriter().write("500 Exception Detail:" + Arrays.toString(e.getStackTrace()));
       }
    }

    public void doDispatch(HttpServletRequest req,HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        //路径前缀
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");

        //检查是否匹配
        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found。。。。。");
            return;
        }

        //获取URL里面的参数
        Map<String,String[]> param = req.getParameterMap();

        Method method = (Method) this.handlerMapping.get(url);


        /**
         * 待实现参数映射
         */
//        //1.形参位置和参数名字先建立映射，做缓存
//        Map<String,Integer> paramIndexMapping = new HashMap<>();
//        Annotation[][] pa = method.getParameterAnnotations();
//        for (int i = 0 ; i<pa.length;i++){
//            for (Annotation a : pa[i]){
//                if (a instanceof param){
//                    String paramName
//                }
//            }
//        }
//        //2.根据参数位置匹配参数名字，从URL中取到参数名字对应的值



        //反射拿类名
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        //3.组成动态实际参数列表，传递反射调用(暂时硬编码保证其他工能可用)
        method.invoke(ioc.get(beanName),new Object[]{req,resp,param.get("name")[0]});


    }

    @Override
    public void init(ServletConfig config) throws ServletException {

        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2.扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //3.初始化所有相关类的实例并放入IoC容器中
        doInstance();
        //4.完成依赖注入
        doAutowired();
        //5.初始化HandlerMapping
        doInitHandlerMapping();

        System.out.println("My Spring framework is init...");
    }

    //5.初始化HandlerMapping
    private void doInitHandlerMapping() {
        if (ioc.isEmpty()){return;}

        for (Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(FoTenController.class)){continue;}

            //类controller路径
            String baseUrl = "";
            if (clazz.isAnnotationPresent(FoTenRequestMapping.class)){
                FoTenRequestMapping requestMapping = clazz.getAnnotation(FoTenRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //方法路径
            //只迭代public
            for (Method method : clazz.getMethods()){
                if (!method.isAnnotationPresent(FoTenRequestMapping.class)){continue;}
                FoTenRequestMapping requestMapping = method.getAnnotation(FoTenRequestMapping.class);
                String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");
                handlerMapping.put(url,method);
                System.out.println("Mapped:" + url + "," + method);
            }
        }
    }

    //4.完成依赖注入
    private void doAutowired() {
        if (ioc.isEmpty()){return;}
        //判断是否加了注解
        for (Map.Entry<String,Object> entry : ioc.entrySet()){
            //忽略字段修饰符
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields){
                if (!field.isAnnotationPresent(FoTenAutowire.class)){ continue; }
                FoTenAutowire autowired = field.getAnnotation(FoTenAutowire.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)){//根据类型注入
                    beanName = field.getType().getName();
                }
                //强制暴力访问,强吻
                field.setAccessible(true);
                try {
                    //依赖注入，自动赋值
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //3.初始化所有相关类的实例并放入IoC容器中
    private void doInstance() {
        if (classNames.isEmpty()){
            return;
        }
        try {
            for(String className : classNames){
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(FoTenController.class)) {
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    //获取实例
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                }else if (clazz.isAnnotationPresent(FoTenService.class)){
                    //默认首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    //自定义命名beanName
                    FoTenService service = clazz.getAnnotation(FoTenService.class);
                    if (!"".endsWith(service.value())){
                        beanName = service.value();
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    //以接口的名称作为key，接口的实现类作为值，已便于接口注入
                    for (Class<?> i : clazz.getInterfaces()){
                        if (ioc.containsKey(i.getName())){
                            throw new Exception("The beanName is exists (这个bean已存在...)");
                        }
                        ioc.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }


    }

    //处理类名首字母小写
    private String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        //ASCII码大写字符和小写字符相差32
        chars[0] += 32;
        return String.valueOf(chars);
    }

    //2.扫描相关的类
    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()){
            if (file.isDirectory()){
                doScanner(scanPackage + "." +file.getName());
            }else {
                if (!file.getName().endsWith(".class")){continue;}
                //拿到文件名 包名+类名
                String className = (scanPackage + "." + file.getName().replace(".class", ""));
                classNames.add(className);
            }
        }

    }

    //1.加载配置文件
    private void doLoadConfig(String contextConfigLocation) {
        //拿到配置文件的流
        String s = "application.properties";
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(s);
        try {
            //将流加载到properties对象
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null != is){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
