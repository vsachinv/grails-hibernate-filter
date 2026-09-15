grails-hibernate-filter
=======================

# Description
This is a fork of the original [Grails Hibernate Filter Plugin](http://grails.org/plugin/hibernate-filter) 
created from fork [alexkramer/grails-hibernate-filter](https://github.com/alexkramer/grails-hibernate-filter) 
to make it work with Grails 4.x through Apache Grails 7.0.x, Hibernate 5, and GORM 7 through 9.

This repo contains two projects:
  
1.  hibernate-filter-plugin - with plugin code
1.  hibernate-filter-example - with example application using plugin 

# Usage

## Build Plugin File

Clone the repository and execute in main directory command:

    ./gradlew hibernate-filter-plugin:jar
    
You can publish it to your maven local repository using:

    ./gradlew hibernate-filter-plugin:publishToMavenLocal
    
## Running example application

To run example application use command:

    ./gradlew hibernate-filter-example:bootRun
    
## Installation

Add dependency in build.gradle:

for Grails < 3.x

    repositories {
        maven { url "https://dl.bintray.com/goodstartgenetics/grails3-plugins/" }
    }
    
    dependencies {
        compile "org.grails.plugins:hibernate-filter-plugin:0.5.5"
    }
    
for Grails 4.x    

        repositories {
            maven { url "https://maven.pkg.github.com/vsachinv/grails-hibernate-filter" }
        }
        
        dependencies {
            compile "org.grails.plugins:hibernate-filter-plugin:4.0-M2"
        }

for Grails 5.x

        repositories {
            maven { url "https://maven.pkg.github.com/vsachinv/grails-hibernate-filter" }
        }
        
        dependencies {
            compile "org.grails.plugins:hibernate-filter-plugin:5.0-M1"
        }

for Grails 6.x

        dependencies {
            implementation "org.grails.plugins:hibernate-filter-plugin:6.0-M1"
        }

for Apache Grails 7.0.x (Java 17+, Hibernate 5.6 jakarta)

        repositories {
            maven { url "https://maven.pkg.github.com/vsachinv/grails-hibernate-filter" }
        }
        
        dependencies {
            implementation "org.grails.plugins:hibernate-filter-plugin:7.0.0-M1"
        }

Note: 7.0.0-M1 targets Grails 7.0.x only. Grails 7.1+ ships Hibernate 6, which this
plugin does not yet support (see `UPGRADE_PLAN.md`, section 9).

# Usage

Please refer to this project's [wiki](https://github.com/alexkramer/grails-hibernate-filter/wiki) for usage.
