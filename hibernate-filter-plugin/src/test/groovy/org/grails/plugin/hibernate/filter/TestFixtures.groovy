package org.grails.plugin.hibernate.filter

/**
 * Stand-in for a GORM domain class that declares a {@code hibernateFilters} block.
 * The closure is mutable so each feature method can install its own DSL body.
 */
class FilteredDomain {
    static Closure hibernateFilters
}

/** Stand-in for a domain class with no {@code hibernateFilters} block. */
class UnfilteredDomain {
}

/** Domain-like class used to exercise {@link HibernateFilterDomainProxy} dispatch. */
class ProxiedDomain {
    String greet(String who) { "hi $who" }
    static List<String> list() { ['a', 'b'] }
}

/** Stand-in for a Grails artefact (controller, service) that receives alias-domain proxies. */
class ArtefactFixture {
}
