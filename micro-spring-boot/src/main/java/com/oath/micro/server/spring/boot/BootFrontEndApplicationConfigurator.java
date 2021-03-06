package com.oath.micro.server.spring.boot;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequestListener;

import com.oath.cyclops.types.persistent.PersistentList;
import com.oath.micro.server.GlobalState;
import com.oath.micro.server.module.MicroserverEnvironment;
import cyclops.reactive.ReactiveSeq;
import cyclops.reactive.collections.immutable.LinkedListX;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.ContextLoader;


import com.oath.micro.server.config.Config;
import com.oath.micro.server.module.Module;
import com.oath.micro.server.module.ModuleDataExtractor;
import com.oath.micro.server.servers.FilterConfigurer;
import com.oath.micro.server.servers.ServletConfigurer;
import com.oath.micro.server.servers.ServletContextListenerConfigurer;
import com.oath.micro.server.servers.model.FilterData;
import com.oath.micro.server.servers.model.ServerData;
import com.oath.micro.server.servers.model.ServletData;
import com.oath.micro.server.spring.SpringBuilder;

public class BootFrontEndApplicationConfigurator  extends SpringBootServletInitializer implements SpringBuilder {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	public ConfigurableApplicationContext createSpringApp(Config config, Class...classes)  {
		
		
		List<Class> classList = new ArrayList<Class>();
		classList.addAll(Arrays.asList(classes));
		classList.add(JerseySpringBootFrontEndApplication.class);
		classList.add(MyWebAppInitializer.class);

		SpringApplicationBuilder builder = new SpringApplicationBuilder(classList.toArray(new Class[0]));
		
		new JerseySpringBootFrontEndApplication(classList).config(builder);



		return builder.application().run();
	}

    @Override
    public Class[] classes(Config config, Class... classes) {
        List<Class> classList = new ArrayList<Class>();
        classList.addAll(Arrays.asList(classes));
        classList.add(JerseySpringBootFrontEndApplication.class);
        classList.add(MyWebAppInitializer.class);

        return ReactiveSeq.fromIterable(new JerseySpringBootFrontEndApplication(classList).classes).appendAll(classList)
            .toArray(i->new Class[i]);

    }

    @Component
	static class MyWebAppInitializer implements ServletContextInitializer {
	
		private final MicroserverEnvironment microserverEnvironment;
		private final Module module;
		private final ApplicationContext rootContext;
		@Autowired(required=false)
		public MyWebAppInitializer(MicroserverEnvironment env, ApplicationContext rootContext, Module m){
			this.microserverEnvironment = env;
			this.rootContext = rootContext;
			this.module = m;
		}
		@Autowired(required=false)
		public MyWebAppInitializer(MicroserverEnvironment env, ApplicationContext rootContext){
			this(env,rootContext, GlobalState.state.getModules().firstValue(null));
		}
		
		@Override
		public void onStartup(ServletContext webappContext) throws ServletException {
			
			ModuleDataExtractor extractor = new ModuleDataExtractor(module);
			microserverEnvironment.assureModule(module);
			String fullRestResource = "/" + module.getContext() + "/*";

			ServerData serverData = new ServerData(microserverEnvironment.getModuleBean(module).getPort(),
					Arrays.asList(),
					rootContext, fullRestResource, module);
			List<FilterData> filterDataList = extractor.createFilteredDataList(serverData);
			List<ServletData> servletDataList = extractor.createServletDataList(serverData);
			new ServletConfigurer(serverData, LinkedListX.fromIterable(servletDataList)).addServlets(webappContext);

			new FilterConfigurer(serverData, LinkedListX.fromIterable(filterDataList)).addFilters(webappContext);
			PersistentList<ServletContextListener> servletContextListenerData = LinkedListX.fromIterable(module.getListeners(serverData)).filter(i->!(i instanceof ContextLoader));
		    PersistentList<ServletRequestListener> servletRequestListenerData =	LinkedListX.fromIterable(module.getRequestListeners(serverData));
			
			new ServletContextListenerConfigurer(serverData, servletContextListenerData, servletRequestListenerData).addListeners(webappContext);
			
		}
		
	}
	

}
