package org.grails.plugin.hibernate.filter

import org.grails.datastore.mapping.model.PersistentEntity
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.engine.spi.FilterDefinition
import org.hibernate.mapping.Collection
import org.hibernate.mapping.PersistentClass
import org.hibernate.type.LongType
import org.hibernate.type.StringType
import org.hibernate.type.TypeResolver
import spock.lang.Specification

/**
 * Exercises the {@code hibernateFilters} DSL: each feature installs a closure on
 * {@link FilteredDomain} and checks what the builder registers on the Hibernate metadata.
 */
class HibernateFilterBuilderSpec extends Specification {

    InFlightMetadataCollector mappings = Mock()
    PersistentClass persistentClass = Mock()
    TypeResolver typeResolver = Mock()
    Map<String, FilterDefinition> filterDefinitions = [:]

    def setup() {
        clearHolder()
        mappings.getFilterDefinitions() >> filterDefinitions
        mappings.getTypeResolver() >> typeResolver
    }

    def cleanup() {
        clearHolder()
        FilteredDomain.hibernateFilters = null
    }

    private static void clearHolder() {
        DefaultHibernateFiltersHolder.defaultFilters.clear()
        DefaultHibernateFiltersHolder.domainAliasProxies.clear()
        DefaultHibernateFiltersHolder.defaultFilterCallbacks.clear()
    }

    private PersistentEntity entity(Map opts = [:]) {
        PersistentEntity pe = Mock()
        Class javaClass = opts.javaClass ?: FilteredDomain
        pe.getJavaClass() >> javaClass
        pe.getName() >> (opts.name ?: javaClass.name)
        pe.isRoot() >> (opts.containsKey('root') ? opts.root : true)
        pe.getParentEntity() >> opts.parent
        pe.newInstance() >> { javaClass.getDeclaredConstructor().newInstance() }
        pe
    }

    private HibernateFilterBuilder build(PersistentEntity pe = entity()) {
        new HibernateFilterBuilder(mappings, pe, persistentClass)
    }

    // ---- filter definitions ----------------------------------------------------------------

    def "a simple filter registers a definition and attaches it to the entity"() {
        given:
        FilteredDomain.hibernateFilters = { activeFilter(condition: 'active = true') }

        when:
        build()

        then:
        1 * mappings.addFilterDefinition({ FilterDefinition d ->
            d.filterName == 'activeFilter' && d.defaultFilterCondition == 'active = true' && d.parameterNames.empty
        })
        1 * persistentClass.addFilter('activeFilter', 'active = true', true, [:], [:])
        DefaultHibernateFiltersHolder.defaultFilters.empty
    }

    def "named parameters are typed positionally from the 'types' option"() {
        given:
        FilteredDomain.hibernateFilters = {
            byNameAndMinId(condition: ':name = name and id > :minId', types: 'string, long')
        }
        typeResolver.basic('string') >> StringType.INSTANCE
        typeResolver.basic('long') >> LongType.INSTANCE

        when:
        build()

        then:
        1 * mappings.addFilterDefinition({ FilterDefinition d ->
            d.parameterNames == ['name', 'minId'] as Set &&
                    d.getParameterType('name').is(StringType.INSTANCE) &&
                    d.getParameterType('minId').is(LongType.INSTANCE)
        })
    }

    def "'paramTypes' is accepted as an alias for 'types'"() {
        given:
        FilteredDomain.hibernateFilters = { byId(condition: 'id = :id', paramTypes: 'long') }
        typeResolver.basic('long') >> LongType.INSTANCE

        when:
        build()

        then:
        1 * mappings.addFilterDefinition({ FilterDefinition d -> d.getParameterType('id').is(LongType.INSTANCE) })
    }

    def "a parameter used more than once in the condition is typed only once"() {
        given:
        FilteredDomain.hibernateFilters = { avoid(condition: 'id > :avoid or id < :avoid', types: 'long') }

        when:
        build()

        then:
        1 * typeResolver.basic('long') >> LongType.INSTANCE
        1 * mappings.addFilterDefinition({ FilterDefinition d -> d.parameterNames == ['avoid'] as Set })
    }

    def "a filter name that already has a definition is reused and its stored condition applied"() {
        given:
        filterDefinitions['shared'] = new FilterDefinition('shared', 'status = 1', [:])
        FilteredDomain.hibernateFilters = { shared() }

        when:
        build()

        then:
        0 * mappings.addFilterDefinition(_)
        1 * persistentClass.addFilter('shared', 'status = 1', true, [:], [:])
    }

    def "an explicit condition overrides the stored one without redefining the filter"() {
        given:
        filterDefinitions['shared'] = new FilterDefinition('shared', 'status = 1', [:])
        FilteredDomain.hibernateFilters = { shared(condition: 'status = 2') }

        when:
        build()

        then:
        0 * mappings.addFilterDefinition(_)
        1 * persistentClass.addFilter('shared', 'status = 2', true, [:], [:])
    }

    // ---- default filters and callbacks ------------------------------------------------------

    def "'default: true' records the filter as a default filter"() {
        given:
        FilteredDomain.hibernateFilters = { activeFilter(condition: 'active = true', default: true) }

        when:
        build()

        then:
        DefaultHibernateFiltersHolder.defaultFilters == ['activeFilter']
        DefaultHibernateFiltersHolder.defaultFilterCallbacks.isEmpty()
    }

    def "a closure for 'default' is recorded as a callback instead of a default filter"() {
        given:
        Closure decide = { -> false }
        FilteredDomain.hibernateFilters = { conditional(condition: 'enabled = true', default: decide) }

        when:
        build()

        then:
        DefaultHibernateFiltersHolder.defaultFilters.empty
        DefaultHibernateFiltersHolder.defaultFilterCallbacks['conditional'].is(decide)
    }

    // ---- collections -------------------------------------------------------------------------

    def "'collection' attaches the filter to the collection binding, not the entity"() {
        given:
        Collection bars = Mock()
        FilteredDomain.hibernateFilters = { barFilter(collection: 'bars', condition: 'enabled = true') }

        when:
        build()

        then:
        1 * mappings.getCollectionBinding("${FilteredDomain.name}.bars") >> bars
        1 * bars.addFilter('barFilter', 'enabled = true', true, [:], [:])
        0 * bars.addManyToManyFilter(*_)
        0 * persistentClass.addFilter(*_)
    }

    def "'joinTable: true' on a collection uses the many-to-many filter"() {
        given:
        Collection pens = Mock()
        FilteredDomain.hibernateFilters = { penFilter(collection: 'pens', condition: 'status = 1', joinTable: true) }

        when:
        build()

        then:
        1 * mappings.getCollectionBinding("${FilteredDomain.name}.pens") >> pens
        1 * pens.addManyToManyFilter('penFilter', 'status = 1', true, [:], [:])
        0 * pens.addFilter(*_)
    }

    def "a collection missing on a subclass is looked up on the parent entity"() {
        given:
        Collection inherited = Mock()
        PersistentEntity parent = entity(javaClass: UnfilteredDomain)
        PersistentEntity child = entity(name: 'com.example.Child', root: false, parent: parent)
        FilteredDomain.hibernateFilters = { barFilter(collection: 'bars', condition: 'enabled = true') }

        when:
        build(child)

        then:
        1 * mappings.getCollectionBinding('com.example.Child.bars') >> null
        1 * mappings.getCollectionBinding("${UnfilteredDomain.name}.bars") >> inherited
        1 * inherited.addFilter('barFilter', 'enabled = true', true, [:], [:])
    }

    def "a collection missing on a root entity is skipped after the definition is registered"() {
        given:
        FilteredDomain.hibernateFilters = { ghost(collection: 'nothing', condition: 'x = 1') }
        mappings.getCollectionBinding(_) >> null

        when:
        build()

        then:
        1 * mappings.addFilterDefinition({ FilterDefinition d -> d.filterName == 'ghost' })
        0 * persistentClass.addFilter(*_)
        notThrown(Exception)
    }

    // ---- alias domains -----------------------------------------------------------------------

    def "'aliasDomain' on a root entity registers a proxy for later injection"() {
        given:
        FilteredDomain.hibernateFilters = { enabledFilter(condition: 'enabled = true', aliasDomain: 'EnabledDomain') }

        when:
        build()

        then:
        DefaultHibernateFiltersHolder.domainAliasProxies.size() == 1
        with(DefaultHibernateFiltersHolder.domainAliasProxies[0]) {
            aliasName == 'EnabledDomain'
            filterName == 'enabledFilter'
            domainClassInstance instanceof FilteredDomain
        }
    }

    def "'aliasDomain' on a non-root entity is ignored"() {
        given:
        FilteredDomain.hibernateFilters = { enabledFilter(condition: 'enabled = true', aliasDomain: 'EnabledDomain') }

        when:
        build(entity(root: false))

        then:
        DefaultHibernateFiltersHolder.domainAliasProxies.empty
    }

    // ---- errors ------------------------------------------------------------------------------

    def "unsupported argument shapes raise HibernateFilterException naming the class and filter"() {
        given:
        FilteredDomain.hibernateFilters = { broken('not a map') }

        when:
        build()

        then:
        HibernateFilterException e = thrown()
        e.message.contains(FilteredDomain.name)
        e.message.contains('broken')
    }
}
