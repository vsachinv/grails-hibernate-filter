package org.grails.plugin.hibernate.filter

import spock.lang.Specification

class DefaultHibernateFiltersHolderSpec extends Specification {

    def setup() {
        DefaultHibernateFiltersHolder.defaultFilters.clear()
        DefaultHibernateFiltersHolder.domainAliasProxies.clear()
        DefaultHibernateFiltersHolder.defaultFilterCallbacks.clear()
    }

    def cleanup() {
        setup()
    }

    def "records default filter names in declaration order"() {
        when:
        DefaultHibernateFiltersHolder.addDefaultFilter('first')
        DefaultHibernateFiltersHolder.addDefaultFilter('second')

        then:
        DefaultHibernateFiltersHolder.defaultFilters == ['first', 'second']
    }

    def "records alias-domain proxies"() {
        given:
        def proxy = new HibernateFilterDomainProxy(new UnfilteredDomain(), 'Alias', 'filter')

        when:
        DefaultHibernateFiltersHolder.addDomainAliasProxy(proxy)

        then:
        DefaultHibernateFiltersHolder.domainAliasProxies == [proxy]
    }

    def "records default-filter callbacks by filter name, last write wins"() {
        given:
        Closure first = { -> true }
        Closure second = { -> false }

        when:
        DefaultHibernateFiltersHolder.addDefaultFilterCallback('f', first)
        DefaultHibernateFiltersHolder.addDefaultFilterCallback('f', second)

        then:
        DefaultHibernateFiltersHolder.defaultFilterCallbacks.size() == 1
        DefaultHibernateFiltersHolder.defaultFilterCallbacks['f'].is(second)
    }
}
