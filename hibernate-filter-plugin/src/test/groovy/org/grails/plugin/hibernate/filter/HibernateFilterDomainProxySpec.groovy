package org.grails.plugin.hibernate.filter

import spock.lang.Specification

class HibernateFilterDomainProxySpec extends Specification {

    List<String> filtersApplied = []

    def setup() {
        // Simulate the static method the plugin injects into every domain class at startup.
        ProxiedDomain.metaClass.static.withHibernateFilter = { String name, Closure body ->
            filtersApplied << name
            body()
        }
    }

    def cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(ProxiedDomain)
    }

    def "instance method calls are forwarded to the domain instance inside the named filter"() {
        given:
        def proxy = new HibernateFilterDomainProxy(new ProxiedDomain(), 'ActiveProxied', 'activeFilter')

        when:
        def result = proxy.greet('bob')

        then:
        result == 'hi bob'
        filtersApplied == ['activeFilter']
    }

    def "static method calls are forwarded to the domain class inside the named filter"() {
        given:
        def proxy = new HibernateFilterDomainProxy(new ProxiedDomain(), 'ActiveProxied', 'activeFilter')

        when:
        def result = proxy.list()

        then:
        result == ['a', 'b']
        filtersApplied == ['activeFilter']
    }

    def "every call re-applies the filter"() {
        given:
        def proxy = new HibernateFilterDomainProxy(new ProxiedDomain(), 'ActiveProxied', 'activeFilter')

        when:
        proxy.greet('a')
        proxy.list()

        then:
        filtersApplied == ['activeFilter', 'activeFilter']
    }

    def "toString identifies alias and domain class"() {
        expect:
        new HibernateFilterDomainProxy(new ProxiedDomain(), 'ActiveProxied', 'activeFilter').toString() ==
                "HibernateFilterDomainProxy: alias=ActiveProxied, domain=${ProxiedDomain.name}"
    }
}
