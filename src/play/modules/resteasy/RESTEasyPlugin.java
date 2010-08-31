/*
    This file is part of resteasy-play-module.
    
    Copyright Lunatech Research 2010

    resteasy-play-module is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    resteasy-play-module is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU General Lesser Public License
    along with resteasy-play-module.  If not, see <http://www.gnu.org/licenses/>.
*/

package play.modules.resteasy;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.Modifier;

import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.Play.Mode;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.db.jpa.JPAPlugin;
import play.mvc.Http;
import play.mvc.Scope;
import play.mvc.Http.Request;
import play.mvc.Http.Response;

public class RESTEasyPlugin extends PlayPlugin {

	private static void log(String message, Object... params){
		Logger.info("RESTEasy plugin: "+message, params);
	}

	private ResteasyDeployment deployment;
	private String path;
	private Map<String, Class<?>> resourceClasses = new HashMap<String, Class<?>>();
	private Map<String, Class<?>> providerClasses = new HashMap<String, Class<?>>();
	private boolean started;

	@Override
	public void onConfigurationRead(){
		log("Configuration read");
		path = Play.configuration.getProperty("resteasy.path");
		if(path == null)
			path = "/rest";
	}

	@Override
	public void onApplicationStart(){
		fixClassLoader();
		try{
			if(!started){
				log("Starting RESTEasy");
				deploy();
				log("RESTEasy started");
				started = true;
			}else if(Play.mode != Mode.PROD){
				deploy();
			}
		}catch(Throwable t){
			t.printStackTrace();
			throw new RuntimeException(t);
		}
	}

	private void deploy() {
		List<ApplicationClass> classes = Play.classes.all();
		deployment = new ResteasyDeployment();
		// classes
		for(ApplicationClass klass : classes){
			if(!isJAXRSEntity(klass.javaClass, Path.class))
				continue;
			log("Found resource class: %s",klass.name);
			resourceClasses.put(klass.name, klass.javaClass);
		}
		deployment.setResourceClasses(new ArrayList<String>(resourceClasses.keySet()));
		// providers
		for(ApplicationClass klass : classes){
			if(!isJAXRSEntity(klass.javaClass, Provider.class))
				continue;
			log("Found provider class: %s",klass.name);
			providerClasses.put(klass.name, klass.javaClass);
		}
		deployment.setProviderClasses(new ArrayList<String>(providerClasses.keySet()));
		deployment.start();
	}

	private void fixClassLoader() {
		Thread.currentThread().setContextClassLoader(Play.classloader);
	}

	private boolean isJAXRSEntity(Class<?> javaClass, Class<? extends Annotation> annotation) {
		if(!hasAnnotation(javaClass, annotation))
			return false;
		if(javaClass.isInterface()
				|| Modifier.isAbstract(javaClass.getModifiers()))
			return false;
		return true;
	}

	private boolean hasAnnotation(Class<?> type, Class<? extends Annotation> annotation){
		if(type == null)
			return false;
		if(type.isAnnotationPresent(annotation))
			return true;
		for(Class<?> interfaceType : type.getInterfaces())
			if(hasAnnotation(interfaceType, annotation))
				return true;
		return hasAnnotation(type.getSuperclass(), annotation);
	}

	@Override
	public boolean rawInvocation(Http.Request request,
			Http.Response response)
	throws Exception{
		log("Got a request: "+request.url);
		if(request.path.startsWith(path+"/") 
				|| request.path.equals(path)){
			log("Got a request for us: "+request.url);
			Play.detectChanges();
			try{
				fixClassLoader();
				setupPlay(request, response);
				JPAPlugin.startTx(false);
				Dispatcher dispatcher = deployment.getDispatcher();
				ResteasyProviderFactory factory = deployment.getProviderFactory();
				HttpRequest restReq = new RESTEasyRequestWrapper(request, path);
				HttpResponse restRep = new RESTEasyResponseWrapper(request, response, factory);
				dispatcher.invoke(restReq, restRep);
				JPAPlugin.closeTx(false);
			}catch(Throwable t){
				JPAPlugin.closeTx(true);
				t.printStackTrace();
				throw new RuntimeException(t);
			}
			return true;
		}
		return false;
	}

	private void setupPlay(Request request, Response response) {
        Http.Request.current.set(request);
        Http.Response.current.set(response);

        Scope.Params.current.set(new Scope.Params());
        Scope.RenderArgs.current.set(new Scope.RenderArgs());
        // FIXME
        // can't do that:
        //Scope.Session.current.set(Scope.Session.restore());
        Scope.Session.current.set(new Scope.Session());
        // nor that
        //Scope.Flash.current.set(Scope.Flash.restore());
	}

	@Override
	public List<ApplicationClass> onClassesChange(List<ApplicationClass> modified) {
		log("Classes change: "+modified.size());
		if(Play.mode != Mode.PROD)
			deploy();
		return super.onClassesChange(modified);
	}

	@Override
	public void detectChange(){
		//log("Detect change");
	}
}
