package org.openfoot.model

/**
 * The country and continent identifiers the simulation names directly.
 *
 * Countries and continents are plain integers because that is how the original
 * data files index them, and the full tables are data rather than code. Only
 * the few the formulas single out by name belong here. Everything else reaches
 * the simulation through the dataset.
 *
 * These live in the model rather than next to the formula that reads them
 * because the dataset layer needs them too, and the dataset cannot depend on
 * the engine without inverting the module graph.
 */
object Country {

    /** Brazil, singled out by the continental handicap of section 3.3. */
    @SpecRef("FORMAT-SPEC, paises")
    const val BRAZIL = 29

    /** Europe, singled out by the club world cup handicap of section 3.3. */
    @SpecRef("4.4.1")
    const val EUROPE_CONTINENT = 0

    /** South America, which with Europe is exempt from the prestige discount of section 5.5. */
    @SpecRef("4.4.1")
    const val SOUTH_AMERICA_CONTINENT = 1
}
