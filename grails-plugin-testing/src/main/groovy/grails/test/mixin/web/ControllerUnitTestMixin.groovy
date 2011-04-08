/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.test.mixin.web

import grails.spring.BeanBuilder
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.util.GrailsWebUtil
import org.codehaus.groovy.grails.commons.UrlMappingsArtefactHandler
import org.codehaus.groovy.grails.commons.metaclass.MetaClassEnhancer
import org.codehaus.groovy.grails.plugins.converters.ConvertersPluginSupport
import org.codehaus.groovy.grails.plugins.converters.api.ConvertersControllersApi
import org.codehaus.groovy.grails.plugins.testing.GrailsMockHttpServletResponse
import org.codehaus.groovy.grails.plugins.web.ServletsGrailsPluginSupport
import org.codehaus.groovy.grails.plugins.web.api.ControllerTagLibraryApi
import org.codehaus.groovy.grails.plugins.web.api.ControllersApi
import org.codehaus.groovy.grails.plugins.web.api.ControllersMimeTypesApi
import org.codehaus.groovy.grails.plugins.web.mimes.MimeTypesGrailsPlugin
import org.codehaus.groovy.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.codehaus.groovy.grails.web.mapping.DefaultLinkGenerator
import org.codehaus.groovy.grails.web.mapping.UrlMappingsHolderFactoryBean
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.util.ClassUtils
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.mock.web.MockHttpSession
import org.codehaus.groovy.grails.commons.ControllerArtefactHandler

/**
 * A mixin that can be applied to a unit test in order to test controllers
 *
 * @author Graeme Rocher
 * @since 1.4
 */
class ControllerUnitTestMixin extends GrailsUnitTestMixin{

    GrailsWebRequest webRequest
    MockHttpServletRequest request
    MockHttpServletResponse response
    MockServletContext servletContext


    MockHttpSession getSession() {
        request.session
    }

    GrailsParameterMap getParams() {
        webRequest.getParams()
    }


    @BeforeClass
    static void configureGrailsWeb() {
        if(applicationContext == null) {
            initGrailsApplication()
        }
        BeanBuilder bb = new BeanBuilder()

        defineBeans(new MimeTypesGrailsPlugin().doWithSpring)
        defineBeans {
            grailsLinkGenerator(DefaultLinkGenerator, config?.grails?.serverURL ?: "http://localhost:8080")

            final classLoader = ControllerUnitTestMixin.class.getClassLoader()
            if(ClassUtils.isPresent("UrlMappings", classLoader )) {
                grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, classLoader.loadClass("UrlMappings"))
            }
            grailsUrlMappingsHolder(UrlMappingsHolderFactoryBean) {
                grailsApplication = GrailsUnitTestMixin.grailsApplication
            }
            convertersConfigurationInitializer(ConvertersConfigurationInitializer)
        }


        applicationContext.getBean("convertersConfigurationInitializer").initialize(grailsApplication)
    }

    @Before
    void bindGrailsWebRequest() {
        if(applicationContext.isActive()) {
            applicationContext.refresh()
        }

        servletContext = new MockServletContext()
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext)
        servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, applicationContext)

        applicationContext.servletContext = servletContext

        ServletsGrailsPluginSupport.enhanceServletApi()
        ConvertersPluginSupport.enhanceApplication(grailsApplication,applicationContext)

        request = new MockHttpServletRequest()
        response = new GrailsMockHttpServletResponse()
        webRequest = GrailsWebUtil.bindMockWebRequest(applicationContext, request, response)
        request = webRequest.getCurrentRequest()
        response = webRequest.getCurrentResponse()
        servletContext = webRequest.getServletContext()
    }

    def mockController(Class controllerClass) {
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, controllerClass)
        MetaClassEnhancer enhancer = new MetaClassEnhancer()

        enhancer.addApi(new ControllersApi())
        enhancer.addApi(new ConvertersControllersApi())
        enhancer.addApi(new ControllerTagLibraryApi()) // TODO: Add lazy mocking lookup for tag invocation
        enhancer.addApi(new ControllersMimeTypesApi())
        enhancer.enhance(controllerClass.metaClass)

        defineBeans {
            "${controllerClass.name}"(controllerClass) { bean ->
                bean.scope = 'prototype'
                bean.autowire = true

            }
        }
        return applicationContext.getBean(controllerClass.name)
    }

    @After
    void clearGrailsWebRequest() {
        webRequest = null
        request = null
        response = null
        servletContext = null
        RequestContextHolder.setRequestAttributes(null)
    }
}
