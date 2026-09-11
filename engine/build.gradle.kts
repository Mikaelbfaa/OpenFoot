plugins {
    id("openfoot.kotlin-pure")
    id("openfoot.quality")
    `java-test-fixtures`
}

dependencies {
    api(project(":model"))
    api(project(":dataset"))
}
