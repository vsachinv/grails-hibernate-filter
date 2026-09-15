package org.grails.plugin.hibernate.filter

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.engine.spi.FilterDefinition
import org.hibernate.mapping.PersistentClass
import spock.lang.Specification

class HibernateFilterSecondPassSpec extends Specification {

    InFlightMetadataCollector mappings = Mock()
    HibernateMappingContext mappingContext = Mock()

    def setup() {
        DefaultHibernateFiltersHolder.defaultFilters.clear()
        DefaultHibernateFiltersHolder.domainAliasProxies.clear()
        DefaultHibernateFiltersHolder.defaultFilterCallbacks.clear()
        mappings.getFilterDefinitions() >> [:]
    }

    def cleanup() {
        FilteredDomain.hibernateFilters = null
    }

    private PersistentEntity entity(Class javaClass) {
        PersistentEntity pe = Mock()
        pe.getJavaClass() >> javaClass
        pe.getName() >> javaClass.name
        pe.isRoot() >> true
        pe
    }

    def "only entities declaring hibernateFilters are processed"() {
        given:
        FilteredDomain.hibernateFilters = { statusFilter(condition: 'status = 1') }
        PersistentClass filteredClass = Mock()
        PersistentClass plainClass = Mock()
        mappingContext.getPersistentEntities() >> [entity(FilteredDomain), entity(UnfilteredDomain)]

        when:
        new HibernateFilterSecondPass(mappings, mappingContext).doSecondPass([
                (FilteredDomain.name)  : filteredClass,
                (UnfilteredDomain.name): plainClass,
        ])

        then:
        1 * mappings.addFilterDefinition({ FilterDefinition d -> d.filterName == 'statusFilter' })
        1 * filteredClass.addFilter('statusFilter', 'status = 1', true, [:], [:])
        0 * plainClass._
    }

    def "no entities means no metadata changes"() {
        given:
        mappingContext.getPersistentEntities() >> []

        when:
        new HibernateFilterSecondPass(mappings, mappingContext).doSecondPass([:])

        then:
        0 * mappings.addFilterDefinition(_)
    }
}
