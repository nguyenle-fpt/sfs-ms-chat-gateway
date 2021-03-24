/*
 * PR Builder for sfs-ms-chat-gateway
 * https://warpdrive.dev.symphony.com/jenkins/job/SymphonyOSF/job/sfs-ms-chat-gateway/view/change-requests/
 *
 * Stages:
 * 1) Checkout
 * 2) Build dependencies
 * 3) Build and run unit tests
 * 4) Report unit test status
 * 5) Coverage Analysis
 * 6) Differential Coverage Analysis
 */

@Library(['sym-pipeline@master']) _

import com.symphony.cicd.CICDConstants
import com.symphony.cicd.SymphonyCICDUtils
import com.symphony.cicd.build.BuilderFactory
import com.symphony.cicd.util.GitHubUtils
import com.symphony.cicd.Utils

cicdUtils = new SymphonyCICDUtils()
token = cicdUtils.getSecretTextCredential('symphonyjenkinsauto-token')

// CODEBASE
githubRepository = 'sfs-ms-chat-gateway'
githubOrganisation = 'SymphonyOSF'
branch = env.BRANCH_NAME ?: 'master'

withEnv(['USE_OPENJDK11=true', 'DISABLE_SONAR=true', 'ENABLE_UNIT_TEST_COVERAGE=true']) {
  jenkinsAgentTemplate(this.&setEnvironment)
}

def setEnvironment() {
  withEnv(["PROJECT_TYPE=java",
           "GIT_REPO=${githubRepository}",
           "GIT_ORG=${githubOrganisation}",
           "TOKEN=${token}",
           "GIT_BRANCH=${branch}"]) {
    try {
      stage('Checkout') {
        gitCheckout()
      }

      if (cicdUtils.isPullRequest()) {

        echo "Starting PR Builder"

        stage('Checkout dependencies') {
          checkoutDependencyRepo(githubOrganisation, 'sfs-ms-chassis', env.CHANGE_BRANCH)
          checkoutDependencyRepo(githubOrganisation, 'sfs-ms-admin', env.CHANGE_BRANCH)
        }

        stage('Build dependencies') {
          println "Building Chassis"
          cicdUtils.maven({ cmd = "-s settings.xml -q -f sfs-ms-chassis/pom.xml clean install -DskipTests=true" })

          println "Building Gateway Client"
          cicdUtils.maven({ cmd = "-s settings.xml -q -f pom.xml clean install -DskipTests=true --non-recursive" })
          cicdUtils.maven({ cmd = "-s settings.xml -q -f sfs-ms-chat-gateway-client/pom.xml clean install -DskipTests=true" })

          println "Building Admin Client"
          cicdUtils.maven({ cmd = "-s settings.xml -q -f sfs-ms-admin/pom.xml clean install -DskipTests=true --non-recursive" })
          cicdUtils.maven({ cmd = "-s settings.xml -q -f sfs-ms-admin/sfs-ms-admin-client/pom.xml clean install -DskipTests=true" })
          cicdUtils.maven({ cmd = "-s settings.xml -q -f sfs-ms-admin/sfs-ms-emp-client/pom.xml clean install -DskipTests=true" })
        }

        stage('Build and run unit tests') {
           build()
         }

        stage('Report unit test status') {
          sendUnitTestStatus(cicdUtils)
        }

        // allow skip this stage if commit text contains 'skip-diff-coverage'
        commit = sh(returnStdout: true, script: 'git log -1 --oneline').trim()
        if (!commit.contains('skip-diff-coverage')) {
          stage('Diff Coverage Analysis') {
            gitCheckout() // make sure we use sfs-ms-chat-gateway repo for diffCover
            diffCover(env.CHANGE_TARGET)
          }
        }

      }

    } catch (Exception e) {
      steps.echo "Pipeline failure: ${e}"
      currentBuild.result = 'FAILURE'
    } finally {
      cleanWs()
    }
  }
}

def build() {

  def version = sh (script: '''cat pom.xml | grep "<maven.compiler.target>.*</maven.compiler.target>$" | awk -F\'[><]\' \'{print $3}\'''', returnStdout: true)?.trim()
  version = (version) ?: '11'
  jdk(version) {
      GitHubUtils ghUtils = new GitHubUtils()
      def prHeadHash = ghUtils.getPullRequestHeadHash(env.CHANGE_ID.toInteger())

      echo "CHANGE_TARGET = ${env.CHANGE_TARGET}"
      echo "HASH: PR HEAD ${prHeadHash}"

      try {
        def factoryBuilder = new BuilderFactory(env, steps)
        def builder = factoryBuilder.getBuilder()
        builder.build({
            runUnitTests = true
            measureCoverage = true
        })
      } catch (e) {
        echo "Fails building the project: ${e.message}"
      }
  }
}

def sendUnitTestStatus(SymphonyCICDUtils cicdUtils) {
    echo "[unit-tests] Sending PR notification"
    def utils = new Utils()
    def unitResults = utils.parseUnitTestResults(env.BUILD_URL)
    String testResultMessage
    String reportUrl = env.BUILD_URL + "testReport"
    try {
        testResultMessage = "Test results: ${unitResults.percentage}% pass rate (${unitResults.success} in ${unitResults.total})"
    } catch (Exception e) {
        cicdUtils.addStatusToPullRequest(CICDConstants.UNIT_TESTS_STATUS_NAME, CICDConstants.GH_STATUS_FAILURE, "Unit tests did not run or parsing the results was not possible!", reportUrl)
        throw new RuntimeException("Unit tests did not run or parsing the results was not possible!")
    }
    if (unitResults.success == unitResults.total) {
        cicdUtils.addStatusToPullRequest(CICDConstants.UNIT_TESTS_STATUS_NAME, CICDConstants.GH_STATUS_SUCCESS, testResultMessage, reportUrl)
    } else {
        cicdUtils.addStatusToPullRequest(CICDConstants.UNIT_TESTS_STATUS_NAME, CICDConstants.GH_STATUS_FAILURE, testResultMessage, reportUrl)
    }
}

def checkoutDependencyRepo(org, repo, branch) {
  if (branchExists(org, repo, branch)) {
    targetBranch = branch
  } else {
    targetBranch = 'master'
  }
  println "branch for ${repo} will be ${targetBranch}"
  checkout(org, repo, targetBranch)

}

def checkout(org, repo, branch) {
  echo "Running git checkout for branch=${branch} org=${org} repo=${repo}"
  cicdUtils.gitCloneToSubdirectory(/*targetDir*/ repo, branch, "https://github.com/${org}/${repo}")
}

boolean branchExists(org, repo, branch) {
    withCredentials([[$class: 'StringBinding', credentialsId: 'symphonyjenkinsauto-token', variable: 'TOKEN']]) {
      sh "git remote rm origin \
          && git remote add origin https://${env.TOKEN}:x-oauth-basic@github.com/${org}/${repo}.git"
      branchExistsFlag = sh (script: "git fetch origin 2>&1 | grep ${branch} | wc -l | xargs", returnStdout: true)
      return branchExistsFlag.toBoolean()
    }
}
