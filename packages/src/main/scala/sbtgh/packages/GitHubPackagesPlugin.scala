/*
 * Copyright 2019 Daniel Spiewak
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sbtgh.packages

import sbt._
import Keys._

object GitHubPackagesPlugin extends AutoPlugin {
  @volatile
  private[this] var alreadyWarned = false

  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  object autoImport extends GitHubPackagesKeys {
    type TokenSource = sbtgh.TokenSource
    val TokenSource = sbtgh.TokenSource
  }

  import autoImport._
  import sbtgh.GitHubResolver._

  val authenticationSettings = Seq(
    githubTokenSource := TokenSource.Environment("GITHUB_TOKEN"),

    credentials += {
      val src = githubTokenSource.value
      inferredGitHubCredentials("_", src) match {   // user is ignored by GitHub, so just use "_"
        case Some(creds) =>
          creds

        case None =>
          sys.error(s"unable to locate a valid GitHub token from $src")
      }
    })

  val packagePublishSettings = Seq(
    githubPublishTo := {
      val log = streams.value.log
      val ms = publishMavenStyle.value
      val back = for {
        owner <- githubOwner.?.value
        repo <- githubRepository.?.value
      } yield "GitHub Package Registry" at s"https://maven.pkg.github.com/$owner/$repo"

      back foreach { _ =>
        if (!ms) {
          sys.error("GitHub Packages does not support Ivy-style publication")
        }
      }

      back
    },

    publishTo := {
      val suppress = githubSuppressPublicationWarning.value
      val log = streams.value.log

      githubPublishTo.value orElse {
        GitHubPackagesPlugin synchronized {
          if (!alreadyWarned && !suppress) {
            log.warn("undefined keys `githubOwner` and `githubRepository`")
            log.warn("retaining pre-existing publication settings")
            alreadyWarned = true
          }
        }

        publishTo.value
      }
    },

    resolvers ++= githubOwner.?.value.toSeq.map(Resolver.githubPackages(_)),

    scmInfo := {
      val back = for {
        owner <- githubOwner.?.value
        repo <- githubRepository.?.value
      } yield ScmInfo(url(s"https://github.com/$owner/$repo"), s"scm:git@github.com:$owner/$repo.git")

      back.orElse(scmInfo.value)
    },

    homepage := {
      val back = for {
        owner <- githubOwner.?.value
        repo <- githubRepository.?.value
      } yield url(s"https://github.com/$owner/$repo")

      back.orElse(homepage.value)
    },

    pomIncludeRepository := (_ => false),
    publishMavenStyle := true) ++
    authenticationSettings

  override def projectSettings = packagePublishSettings

  override def buildSettings = Seq(githubSuppressPublicationWarning := false)
}