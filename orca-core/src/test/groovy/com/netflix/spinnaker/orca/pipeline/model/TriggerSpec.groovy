/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model

import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import spock.lang.Specification

class TriggerSpec extends Specification {

  def mapper = OrcaObjectMapper.newInstance()

  def "can parse a trigger with no payload"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    with(trigger) {
      type == "manual"
      user == "afeldman@netflix.com"
      parameters == [foo: "covfefe", bar: "fnord"]
      notifications == []
    }

    where:
    triggerJson = '''
    {
      "type": "manual",
      "user": "afeldman@netflix.com",
      "parameters": {
        "foo": "covfefe",
        "bar": "fnord"
      },
      "notifications": []
    }'''
  }

  def "can parse nested parameters"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    with(trigger) {
      parameters == [simple: "covfefe", object: [foo: "covfefe"], list: ["a", "b", "c"]]
    }

    where:
    triggerJson = '''
    {
      "type": "manual",
      "user": "afeldman@netflix.com",
      "parameters": {
        "simple": "covfefe",
        "object": {
          "foo": "covfefe"
        },
        "list": ["a", "b", "c"]
      },
      "notifications": []
    }'''
  }

  def "defaults properties"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    with(trigger) {
      type == "manual"
      payload == null
      user == "[anonymous]"
      parameters == [:]
      notifications == []
      artifacts == []
    }

    where:
    triggerJson = '''
    {
      "type": "manual"
    }'''
  }

  def "serializing a trigger flattens payload data"() {
    given:
    def asJson = new StringWriter().withCloseable {
      mapper.writeValue(it, trigger)
      it.toString()
    }

    expect:
    def map = mapper.readValue(asJson, Map)
    map.master == trigger.payload.master
    map.job == trigger.payload.job
    map.buildNumber == trigger.payload.buildNumber
    map.propertyFile == trigger.payload.propertyFile

    where:
    trigger = new Trigger(
      "manual",
      new JenkinsTriggerPayload("master", "job", 1337, "covfefe.properties")
    )
  }

  def cronTrigger = '''
{
  "cronExpression":"0 0/12 * 1/1 * ? *",
  "id":"197f94fc-466f-4aa5-9c95-65666e28e8fb",
  "type":"cron",
  "parameters":{},
  "user":"[anonymous]",
  "enabled":true
}'''

  def "can parse a docker trigger"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    with(trigger) {
      payload instanceof DockerTriggerPayload
      payload.account == "theaccount"
      payload.repository == "org/covfefe"
      payload.tag == "foo"
    }

    where:
    triggerJson = '''
{
  "account": "theaccount",
  "enabled": true,
  "job": "the-jenkins-job",
  "master": "master",
  "organization": "org",
  "registry": "registry.covfefe.org",
  "repository": "org/covfefe",
  "tag": "foo",
  "type": "docker"
}
'''
  }

  def "can parse a Git trigger"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    with(trigger) {
      payload instanceof GitTriggerPayload
      payload.source == "stash"
      payload.project == "CAD"
      payload.branch == "bladerunner-release"
      payload.slug == "Main"
    }

    where:
    triggerJson = '''
{
  "project": "CAD",
  "source": "stash",
  "type": "git",
  "job": "CAD-BLADERUNNER-RELEASE-SPINNAKER-TRIGGER",
  "branch": "bladerunner-release",
  "parameters": {
    "ALCHEMIST_GIT_BRANCH": "release",
    "CADMIUM_GIT_BRANCH": "bladerunner-release",
    "ALCHEMIST_JIRA_VERIFICATIONS": "false",
    "ALCHEMIST_DRY_RUN": "false",
    "TEST_FILTER": "/UX|FUNCTIONAL/i",
    "TEST_FILTER_SAFARI": "/UX/i",
    "ALCHEMIST_VERSION": "latest",
    "IGNORE_PLATFORMS_CHROME": "chromeos,linux",
    "IGNORE_PLATFORMS_SAFARI": "none",
    "IGNORE_PLATFORMS_FIREFOX": "linux",
    "IGNORE_PLATFORMS_MSIE": "none",
    "IGNORE_PLATFORMS_CHROMECAST": "none"
  },
  "user": "[anonymous]",
  "enabled": true,
  "slug": "Main",
  "hash": "adb2554e870ae86622f05de2a15f4539030d87a7",
  "master": "cbp"
}
'''
  }

  def "can parse a jenkins trigger"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    with(trigger) {
      payload instanceof JenkinsTriggerPayload
      payload.master == "spinnaker"
      payload.job == "SPINNAKER-package-orca"
      payload.buildNumber == 1509
      payload.propertyFile == ""
      payload.buildInfo.url == "https://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-orca/1509/"
      payload.buildInfo.artifacts.size() == 2
      payload.buildInfo.artifacts[0].fileName == "orca.properties"
      payload.buildInfo.artifacts[0].relativePath == "repo/build/manifest/orca.properties"
      payload.buildInfo.artifacts[1].fileName == "properties.txt"
      payload.buildInfo.artifacts[1].relativePath == "repo/orca-package/build/reports/project/properties.txt"
      payload.buildInfo.scm.size() == 1
      payload.buildInfo.scm[0].name == "origin/master"
      payload.buildInfo.scm[0].branch == "master"
      payload.buildInfo.scm[0].sha1 == "126cadeadf1dd7f202c320a98c3b7f1566708a49"
    }

    where:
    triggerJson = '''
{
  "propertyFile": "",
  "buildInfo": {
    "building": false,
    "fullDisplayName": "SPINNAKER-package-orca #1509",
    "name": "SPINNAKER-package-orca",
    "number": 1509,
    "duration": 124941,
    "timestamp": "1513230062314",
    "result": "SUCCESS",
    "artifacts": [
      {
        "fileName": "orca.properties",
        "relativePath": "repo/build/manifest/orca.properties"
      },
      {
        "fileName": "properties.txt",
        "relativePath": "repo/orca-package/build/reports/project/properties.txt"
      }
    ],
    "url": "https://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-orca/1509/",
    "scm": [
      {
        "name": "origin/master",
        "branch": "master",
        "sha1": "126cadeadf1dd7f202c320a98c3b7f1566708a49"
      }
    ]
  },
  "type": "jenkins",
  "job": "SPINNAKER-package-orca",
  "buildNumber": 1509,
  "parameters": {},
  "user": "[anonymous]",
  "enabled": true,
  "master": "spinnaker"
}
'''
  }

  def "can parse a pipeline trigger"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    with(trigger) {
      payload instanceof PipelineTriggerPayload
      payload.parentExecution.id == "848449c9-b152-4cd6-b22c-bd88d619df77"
    }

    where:
    triggerJson = '''
{
  "parentExecution": {
    "type": "PIPELINE",
    "id": "848449c9-b152-4cd6-b22c-bd88d619df77",
    "application": "fletch_test",
    "name": "Chained 1",
    "buildTime": 1513102084081,
    "canceled": false,
    "canceledBy": null,
    "cancellationReason": null,
    "limitConcurrent": true,
    "keepWaitingPipelines": false,
    "stages": [
      {
        "id": "ea50d669-2a0f-4801-9f89-779a135e5693",
        "refId": "1",
        "type": "wait",
        "name": "Wait",
        "startTime": 1513102084163,
        "endTime": 1513102099340,
        "status": "SUCCEEDED",
        "context": {
          "waitTime": 1,
          "stageDetails": {
            "name": "Wait",
            "type": "wait",
            "startTime": 1513102084163,
            "isSynthetic": false,
            "endTime": 1513102099340
          },
          "waitTaskState": {}
        },
        "outputs": {},
        "tasks": [
          {
            "id": "1",
            "implementingClass": "com.netflix.spinnaker.orca.pipeline.tasks.WaitTask",
            "name": "wait",
            "startTime": 1513102084205,
            "endTime": 1513102099313,
            "status": "SUCCEEDED",
            "stageStart": true,
            "stageEnd": true,
            "loopStart": false,
            "loopEnd": false
          }
        ],
        "syntheticStageOwner": null,
        "parentStageId": null,
        "requisiteStageRefIds": [],
        "scheduledTime": null,
        "lastModified": null
      }
    ],
    "startTime": 1513102084130,
    "endTime": 1513102099392,
    "status": "SUCCEEDED",
    "authentication": {
      "user": "rfletcher@netflix.com",
      "allowedAccounts": [
        "titusmcestaging",
        "titusprodmce",
        "mceprod",
        "persistence_prod",
        "test",
        "mcetest",
        "titusdevint",
        "cpl",
        "dataeng_test",
        "mgmt",
        "prod",
        "dmz_test",
        "sitc_test",
        "titustestvpc",
        "lab_automation_prod",
        "sitc_prod",
        "seg_test",
        "iepprod",
        "lab_automation_test",
        "ieptest",
        "mgmttest",
        "itopsdev",
        "dmz",
        "titusprodvpc",
        "testregistry",
        "dataeng",
        "persistence_test",
        "prodregistry",
        "titusdevvpc",
        "itopsprod",
        "titustestmce"
      ]
    },
    "paused": null,
    "executionEngine": "v3",
    "origin": "deck",
    "trigger": {
      "type": "manual",
      "user": "rfletcher@netflix.com",
      "parameters": {},
      "notifications": []
    },
    "description": null,
    "pipelineConfigId": "241a8418-8649-4f61-bbd1-128bedaef658",
    "notifications": [],
    "initialConfig": {}
  },
  "parentPipelineId": "848449c9-b152-4cd6-b22c-bd88d619df77",
  "isPipeline": true,
  "parentStatus": "SUCCEEDED",
  "type": "pipeline",
  "user": "rfletcher@netflix.com",
  "parentPipelineName": "Chained 1",
  "parameters": {},
  "parentPipelineApplication": "fletch_test"
}
'''
  }

  def pubSubTrigger = '''
{
  "attributeConstraints": {
    "eventType": "OBJECT_FINALIZE"
  },
  "enabled": true,
  "expectedArtifactIds": [
    "bcebbb6b-8262-4efb-99c5-7bed4a6f4d15"
  ],
  "payloadConstraints": {
    "key": "value"
  },
  "pubsubSystem": "google",
  "subscriptionName": "subscription",
  "type": "pubsub"
}'''

  def "a Travis trigger works just like a Jenkins one"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    with(trigger) {
      payload instanceof JenkinsTriggerPayload
      payload.master == "travis-schibsted"
      payload.job == "spt-infrastructure/metrics/master"
      payload.buildNumber == 245
      payload.propertyFile == null
      payload.buildInfo.url == "https://travis.schibsted.io/spt-infrastructure/metrics/builds/2685930"
    }

    where:
    triggerJson = """{
  "account": null,
  "branch": null,
  "buildInfo": {
    "artifacts": [
      {
        "displayPath": "metrics_0.0.245.2685931_amd64.deb",
        "fileName": "metrics_0.0.245.2685931_amd64.deb",
        "name": "metrics",
        "reference": "metrics_0.0.245.2685931_amd64.deb",
        "relativePath": "metrics_0.0.245.2685931_amd64.deb",
        "type": "deb",
        "version": "0.0.245.2685931"
      }
    ],
    "building": false,
    "duration": 72,
    "name": "spt-infrastructure/metrics",
    "number": 245,
    "result": "SUCCESS",
    "scm": [
      {
        "branch": "master",
        "committer": "Jørgen Jervidalo",
        "compareUrl": "https://github.schibsted.io/spt-infrastructure/metrics/compare/ba3d6c6eb3c6...c8c8199027ae",
        "message": "Add logging if error while creating metrics. Some cleanup.",
        "name": "master",
        "sha1": "c8c8199027ae53e5a31503504d74329cfb477f14",
        "timestamp": "2018-01-25T12:00:45Z"
      }
    ],
    "timestamp": "1516881731000",
    "url": "https://travis.schibsted.io/spt-infrastructure/metrics/builds/2685930"
  },
  "buildNumber": 245,
  "constraints": null,
  "cronExpression": null,
  "digest": null,
  "enabled": true,
  "expectedArtifactIds": null,
  "hash": null,
  "id": null,
  "job": "spt-infrastructure/metrics/master",
  "lastSuccessfulExecution": null,
  "master": "travis-schibsted",
  "parameters": {},
  "project": null,
  "propertyFile": null,
  "pubsubSystem": null,
  "repository": null,
  "runAsUser": "spt-delivery@schibsted.com",
  "secret": null,
  "slug": null,
  "source": "github",
  "subscriptionName": null,
  "tag": null,
  "type": "travis",
  "user": "spt-delivery@schibsted.com"
}
"""
  }
}
