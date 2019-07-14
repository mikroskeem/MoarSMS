import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP

plugins {
    kotlin("jvm") version "1.3.41"
    id("com.github.johnrengelman.shadow") version "5.0.0"
    id("net.minecrell.licenser") version "0.4.1"
    id("net.minecrell.plugin-yml.bukkit") version "0.3.0"
}

group = "eu.mikroskeem"
version = "0.0.3-SNAPSHOT"

val nettyVersion = "4.1.19.Final"
val paperApiVersion = "1.12.1-R0.1-SNAPSHOT"
val bstatsVersion = "1.2"

repositories {
    mavenLocal()
    mavenCentral()

    maven("https://repo.destroystokyo.com/repository/maven-public/")
    maven("http://repo.bstats.org/content/repositories/releases/")
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:$paperApiVersion") {
        exclude(module = "bungeecord-chat")
    }
    compileOnly("io.netty:netty-all:$nettyVersion")

    implementation(kotlin("stdlib-jdk8"))
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
        create("moarsms") {
            description = "Reload MoarSMS"
            usage = "/<command>"
            permissionMessage = "Nope"
            permission = "moarsms.moarsms"
        }
    }

    permissions {
        create("moarsms.moarsms") {
            description = "Use /moarsms command"
            default = OP
        }
    }
}

val shadowJar by tasks.getting(ShadowJar::class) {
    relocate("kotlin", "eu.mikroskeem.moarsms.kotlin")
    relocate("org.bstats", "eu.mikroskeem.moarsms.bstats")
}

defaultTasks("build")