plugins {
    id("openfoot.kotlin-jvm")
    id("openfoot.quality")
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":dataset"))
}

/**
 * The sanity suite keeps every one of its twenty thousand match reports in
 * memory on purpose, so that each band can be read off the same sample, and
 * two such samples plus their ratings sit together. That is more than the
 * default test heap holds once a report carries its final whistle energies,
 * so the worker gets a heap sized for the sample rather than the default.
 */
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
}
