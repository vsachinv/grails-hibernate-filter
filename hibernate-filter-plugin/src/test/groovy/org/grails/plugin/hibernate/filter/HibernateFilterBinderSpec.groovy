package org.grails.plugin.hibernate.filter

import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory
import org.hibernate.boot.spi.InFlightMetadataCollector
import spock.lang.Specification

class HibernateFilterBinderSpec extends Specification {

    def "contribute registers a second pass carrying the GORM mapping context"() {
        given:
        HibernateMappingContext mappingContext = Mock()
        HibernateConnectionSourceFactory factory = Mock()
        factory.getMappingContext() >> mappingContext
        InFlightMetadataCollector metadata = Mock()
        def binder = new HibernateFilterBinder(hibernateConnectionSourceFactory: factory)

        when:
        binder.contribute(metadata, null)

        then:
        1 * metadata.addSecondPass({ HibernateFilterSecondPass sp -> sp.mappingContext.is(mappingContext) })
    }
}
