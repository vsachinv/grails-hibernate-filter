package org.grails.plugin.hibernate.filter

import org.hibernate.Session
import org.hibernate.SessionFactory
import spock.lang.Specification

class HibernateFilterInterceptorSpec extends Specification {

    SessionFactory sessionFactory = Mock()
    HibernateFilterInterceptor interceptor = new HibernateFilterInterceptor(sessionFactory: sessionFactory)

    def setup() {
        DefaultHibernateFiltersHolder.defaultFilters.clear()
    }

    def cleanup() {
        setup()
    }

    def "before() enables every default filter on the current session and lets the request continue"() {
        given:
        Session session = Mock()
        sessionFactory.getCurrentSession() >> session
        DefaultHibernateFiltersHolder.addDefaultFilter('activeFilter')
        DefaultHibernateFiltersHolder.addDefaultFilter('tenantFilter')

        when:
        boolean proceed = interceptor.before()

        then:
        proceed
        1 * session.enableFilter('activeFilter')
        1 * session.enableFilter('tenantFilter')
        0 * session.disableFilter(_)
    }

    def "before() is a no-op when there is no current session"() {
        given:
        sessionFactory.getCurrentSession() >> null
        DefaultHibernateFiltersHolder.addDefaultFilter('activeFilter')

        when:
        boolean proceed = interceptor.before()

        then:
        proceed
        noExceptionThrown()
    }

    def "before() does nothing when no default filters are declared"() {
        given:
        Session session = Mock()
        sessionFactory.getCurrentSession() >> session

        when:
        interceptor.before()

        then:
        0 * session.enableFilter(_)
    }
}
