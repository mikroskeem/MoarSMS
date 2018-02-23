import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP

plugins {
    kotlin("jvm") version "1.2.21"
    id("org.zeroturnaround.gradle.jrebel") version "1.1.8"
    id("com.github.johnrengelman.shadow") version "2.0.2"
    id("net.minecrell.licenser") version "0.3"
    id("net.minecrell.plugin-yml.bukkit") version "0.2.1"
}

val nettyVersion: String by extra
val paperApiVersion: String by extra
val bstatsVersion: String by extra
val gradleWrapperVersion: String by extra

repositories {
    mavenLocal()
    mavenCentral()

    maven {
        name = "destroystokyo-repo"
        setUrl("https://repo.destroystokyo.com/repository/maven-public/")
    }
    maven {
        name = "bstats-repo"
        setUrl("http://repo.bstats.org/content/repositories/releases/")
    }
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:$paperApiVersion") {
        exclude(module = "bungeecord-chat")
    }
    compileOnly("io.netty:netty-all:$nettyVersion")

    implementation(kotlin("stdlib-jdk8", "1.2.21"))
    implementation("org.bstats:bstats-bukkit:$bstatsVersion")
}

license {
    header = rootProject.file("etc/HEADER")
    filter.include("**/*.java")
    filter.include("**/*.kt")
}

bukkit {
    description = "Accept Fortumo HTTP requests and run commands"
    authors = listOf("mikroskeem")
    website = "https://mikroskeem.eu"
    main = "$group.moarsms.bukkit.MoarSMSPlugin"

    commands {
        "moarsms" {
            description = "Reload MoarSMS"
            usage = "/<command>"
            permissionMessage = "Nope"
            permission = "moarsms.moarsms"
        }
    }

    permissions {
        "moarsms.moarsms" {
            description = "Use /moarsms command"
            default = OP
        }
    }
}

val shadowJar by tasks.getting(ShadowJar::class) {
    classifier = "shaded"

    relocate("kotlin", "eu.mikroskeem.moarsms.kotlin")
    relocate("org.bstats", "eu.mikroskeem.moarsms.bstats")
}

val wrapper by tasks.creating(Wrapper::class) {
    gradleVersion = gradleWrapperVersion
    distributionUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
}

// -PuseJRebel=true
if((project.properties["useJRebel"] as? String?)?.toBoolean() == true) {
    tasks.getByName("jar").dependsOn(tasks.getByName("generateRebel"))
} else {
    // if -PdontShade=true is defined, then 'build' won't depend on 'shadowJar' task
    if((project.properties["dontShade"] as? String?)?.toBoolean() == false) {
        tasks.getByName("build").dependsOn(tasks.getByName("shadowJar"))
    }
}

defaultTasks("build")