package org.grails.plugin.hibernate.filter

import org.hibernate.Filter
import org.hibernate.Session
import org.hibernate.SessionFactory
import spock.lang.Specification

class HibernateFilterUtilsSpec extends Specification {

    Session session = Mock()

    def setup() {
        DefaultHibernateFiltersHolder.defaultFilters.clear()
        DefaultHibernateFiltersHolder.domainAliasProxies.clear()
        DefaultHibernateFiltersHolder.defaultFilterCallbacks.clear()
    }

    def cleanup() {
        setup()
        GroovySystem.metaClassRegistry.removeMetaClass(UnfilteredDomain)
        GroovySystem.metaClassRegistry.removeMetaClass(ArtefactFixture)
    }

    // ---- withHibernateFilters -------------------------------------------------------------

    def "withHibernateFilters enables the filters, runs the closure and re-disables only those that were disabled"() {
        given: "'already' is enabled before the call, 'fresh' is not"
        session.getEnabledFilter('already') >> Mock(Filter)
        session.getEnabledFilter('fresh') >> null

        when:
        def result = HibernateFilterUtils.withHibernateFilters(session, ['already', 'fresh']) { -> 'done' }

        then:
        result == 'done'
        1 * session.enableFilter('already')
        1 * session.enableFilter('fresh')
        1 * session.disableFilter('fresh')
        0 * session.disableFilter('already')
    }

    def "withHibernateFilters restores filter state even when the closure throws"() {
        given:
        session.getEnabledFilter('f') >> null

        when:
        HibernateFilterUtils.withHibernateFilters(session, ['f']) { -> throw new IllegalStateException('boom') }

        then:
        thrown(IllegalStateException)
        1 * session.enableFilter('f')
        1 * session.disableFilter('f')
    }

    // ---- withoutHibernateFilters ----------------------------------------------------------

    def "withoutHibernateFilters disables the filters, runs the closure and re-enables only those that were enabled"() {
        given:
        session.getEnabledFilter('on') >> Mock(Filter)
        session.getEnabledFilter('off') >> null

        when:
        def result = HibernateFilterUtils.withoutHibernateFilters(session, ['on', 'off']) { -> 7 }

        then:
        result == 7
        1 * session.disableFilter('on')
        1 * session.disableFilter('off')
        1 * session.enableFilter('on')
        0 * session.enableFilter('off')
    }

    def "withoutHibernateFilters restores filter state even when the closure throws"() {
        given:
        session.getEnabledFilter('f') >> Mock(Filter)

        when:
        HibernateFilterUtils.withoutHibernateFilters(session, ['f']) { -> throw new IllegalStateException('boom') }

        then:
        thrown(IllegalStateException)
        1 * session.disableFilter('f')
        1 * session.enableFilter('f')
    }

    // ---- addDomainClassMethods ------------------------------------------------------------

    def "addDomainClassMethods injects static filter helpers bound to the current session"() {
        given:
        SessionFactory sessionFactory = Mock()
        sessionFactory.getCurrentSession() >> session
        def ctx = [sessionFactory: sessionFactory]
        DefaultHibernateFiltersHolder.addDefaultFilter('dflt')
        HibernateFilterUtils.addDomainClassMethods(UnfilteredDomain, ctx)

        when: "a single named filter is scoped around a closure"
        def result = UnfilteredDomain.withHibernateFilter('named') { -> 42 }

        then:
        result == 42
        1 * session.enableFilter('named')
        1 * session.disableFilter('named')

        when: "all default filters are scoped around a closure"
        UnfilteredDomain.withHibernateFilters { -> }

        then:
        1 * session.enableFilter('dflt')
        1 * session.disableFilter('dflt')

        when: "a single named filter is excluded around a closure"
        session.getEnabledFilter('named') >> Mock(Filter)
        UnfilteredDomain.withoutHibernateFilter('named') { -> }

        then:
        1 * session.disableFilter('named')
        1 * session.enableFilter('named')

        when: "all default filters are excluded around a closure"
        UnfilteredDomain.withoutHibernateFilters { -> }

        then:
        1 * session.disableFilter('dflt')

        when: "a filter is enabled directly"
        Filter enabled = Mock()
        session.enableFilter('direct') >> enabled
        def returned = UnfilteredDomain.enableHibernateFilter('direct')

        then:
        returned.is(enabled)

        when: "a filter is disabled directly"
        UnfilteredDomain.disableHibernateFilter('direct')

        then:
        1 * session.disableFilter('direct')
    }

    // ---- addDomainProxies -----------------------------------------------------------------

    def "addDomainProxies exposes every registered alias proxy as a property on the artefact class"() {
        given:
        def enabledFoo = new HibernateFilterDomainProxy(new UnfilteredDomain(), 'EnabledFoo', 'fooEnabled')
        def activeBar = new HibernateFilterDomainProxy(new UnfilteredDomain(), 'ActiveBar', 'barActive')
        DefaultHibernateFiltersHolder.addDomainAliasProxy(enabledFoo)
        DefaultHibernateFiltersHolder.addDomainAliasProxy(activeBar)

        when:
        HibernateFilterUtils.addDomainProxies(ArtefactFixture)
        def artefact = new ArtefactFixture()

        then:
        artefact.EnabledFoo.is(enabledFoo)
        artefact.ActiveBar.is(activeBar)
    }
}
