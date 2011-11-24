package controllers;
import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import play.Logger;
import play.Play;
import play.modules.resteasy.RESTEasyPlugin;
import play.modules.resteasy.RESTEasyRequestWrapper;
import play.modules.resteasy.RESTEasyResponseWrapper;
import play.mvc.Controller;



public class RESTEasyController extends Controller {
  public static void serve() {
    Logger.info("RESTEasy controller invoked: %s", request.url);
    
    RESTEasyPlugin plugin = Play.plugin(RESTEasyPlugin.class);
    Dispatcher dispatcher = plugin.deployment.getDispatcher();
    ResteasyProviderFactory factory = plugin.deployment.getProviderFactory();
    
    HttpRequest restReq = new RESTEasyRequestWrapper(request, plugin.path);
    HttpResponse restRep = new RESTEasyResponseWrapper(request, response, factory);
    
    // Makes sure the right JAXB parser is loaded, since modules which might reference different parsers (in their 
    // META-INF section) are loaded in a random order by Play! (i.e. Play! classpath order is always undefined) 
    System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.xerces.jaxp.SAXParserFactoryImpl");
    
    dispatcher.invoke(restReq, restRep);
  }
}