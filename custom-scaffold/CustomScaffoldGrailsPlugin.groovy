
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.GrailsControllerClass
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.plugins.DefaultGrailsPlugin
import org.springframework.beans.PropertyValue
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.ApplicationContext
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import groovy.lang.GroovyClassLoader
import org.codehaus.groovy.runtime.DefaultGroovyMethods

class CustomScaffoldGrailsPlugin {
	// the plugin version
	def version = "0.1"
	// the version or versions of Grails the plugin is designed for
	def grailsVersion = "1.3.7 > *"
	// the other plugins this plugin depends on
	def dependsOn = [:]
	// resources that are excluded from plugin packaging
	def pluginExcludes = [
			"grails-app/views/error.gsp"
	]

	// TODO Fill in these fields
	def author = "Your name"
	def authorEmail = ""
	def title = "Plugin summary/headline"
	def description = '''\\
Brief description of the plugin.
'''

	// URL to the plugin's documentation
	def documentation = "http://grails.org/plugin/custom-scaffold"

	def watchedResources = "file:./src/groovy/customscaffold/ScaffoldActions.groovy"
	def observe = ['controllers', 'domainClass']
	def loadAfter = ['controllers']

	def doWithWebDescriptor = { xml ->
	}

	def doWithSpring = {
		scaffoldActions(customscaffold.ScaffoldActions)
		scaffoldedActionMap(HashMap)
	}

	def doWithDynamicMethods = { ctx ->
	}

	def doWithApplicationContext = { ApplicationContext ctx ->

		configureCustomScaffolding(ctx, application)
	}

	def configureCustomScaffolding(ApplicationContext appCtx, app) {
		for (controllerClass in app.controllerClasses) {
			println "configuring controller: ${controllerClass}"
			configureCustomScaffoldingController(appCtx, app, controllerClass)
		}
	}

	def onChange = { event ->

		println event.source.getClass().name

		if (event.source && event.source instanceof Class && application.isControllerClass(event.source)) 
		{
			println "reload controller class"
			GrailsControllerClass controllerClass = application.getControllerClass(event.source.name)
			configureCustomScaffoldingController(event.ctx, event.application, controllerClass)
		}
		else {
			println "reload scaffold in general"
			
			if (event.source instanceof org.springframework.core.io.UrlResource)
			{
				ClassLoader gcl = application.getClassLoader();
				def pluginClass = ((GroovyClassLoader)gcl).parseClass(DefaultGroovyMethods.getText(event.source.getInputStream()));

				def beans = beans {
					scaffoldActions(pluginClass)
				}
				println "registering beans"
				beans.registerBeans(event.ctx)
				println event.ctx.getBean('scaffoldActions')
			}
			configureCustomScaffolding(event.ctx, event.application)
		}
	}

	def onConfigChange = { event ->
	}

	private static GrailsDomainClass getCustomScaffoldedDomainClass(application, GrailsControllerClass controllerClass, scaffoldProperty) {
		GrailsDomainClass domainClass = null

		if (scaffoldProperty) {
			if (scaffoldProperty instanceof Class) {
				domainClass = application.getDomainClass(scaffoldProperty.name)
			}
			else if (scaffoldProperty) {
				scaffoldProperty = controllerClass.packageName ? "${controllerClass.packageName}.${controllerClass.name}" : controllerClass.name
				domainClass = application.getDomainClass(scaffoldProperty)
			}
		}
		return domainClass
	}
	
	public static configureCustomScaffoldingController(ApplicationContext appCtx, GrailsApplication application, GrailsControllerClass controllerClass) {

		def scaffoldProperty = controllerClass.getPropertyValue("customScaffold", Object)

		if (!scaffoldProperty || !appCtx) {
			return
		}
		GrailsDomainClass domainClass = getCustomScaffoldedDomainClass(application, controllerClass, scaffoldProperty)
		def scaffoldActions = appCtx.getBean('scaffoldActions')
		def scaffoldedActionMap = appCtx.getBean('scaffoldedActionMap')
		def javaClass = controllerClass.clazz
		def metaClass = javaClass.metaClass

		def actionProperties = []
		scaffoldActions.properties.each { k,v -> 
			if (k != 'class' && k != 'metaClass')
				actionProperties += k
		} // ['closure1', 'closure2']
		println actionProperties
		
		if (scaffoldedActionMap[controllerClass.logicalPropertyName] == null)
		{
			scaffoldedActionMap[controllerClass.logicalPropertyName] = []
		}
		
		for (actionProp in actionProperties) 
		{
			String propertyName = actionProp

			def mp = metaClass.getMetaProperty(propertyName)

			if (!mp || scaffoldedActionMap[controllerClass.logicalPropertyName]?.contains(propertyName)) {

				scaffoldedActionMap[controllerClass.logicalPropertyName] << propertyName

				Closure propertyValue = scaffoldActions.getProperty(propertyName)
				println scaffoldActions.properties
				metaClass."${GrailsClassUtils.getGetterName(propertyName)}" = {->
					propertyValue.delegate = delegate
					propertyValue.resolveStrategy = Closure.DELEGATE_FIRST
					propertyValue
				}
			}
			else
				println "already exists"

			controllerClass.registerMapping(propertyName)
		}
	}
}
