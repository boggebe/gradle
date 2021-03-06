/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.resource.transport

import org.gradle.api.Transformer
import org.gradle.api.resources.ResourceException
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.ExternalResourceAccessor
import org.gradle.internal.resource.transfer.ExternalResourceLister
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse
import org.gradle.internal.resource.transfer.ExternalResourceUploader
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultExternalResourceRepositoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def resourceAccessor = Mock(ExternalResourceAccessor)
    def resourceUploader = Mock(ExternalResourceUploader)
    def resourceLister = Mock(ExternalResourceLister)
    def repository = new DefaultExternalResourceRepository("repo", resourceAccessor, resourceUploader, resourceLister, resourceAccessor, resourceUploader, new TestBuildOperationExecutor())

    def "creating resource does not access the backing resource"() {
        def name = new ExternalResourceName("resource")

        when:
        repository.resource(name, true)

        then:
        0 * _
    }

    def "can copy content to a file"() {
        def name = new ExternalResourceName("resource")
        def response = Mock(ExternalResourceReadResponse)
        def file = tmpDir.file("out")

        def resource = repository.resource(name, true)

        when:
        def result = resource.writeToIfPresent(file)

        then:
        result.readContentLength == 5
        file.text == "12345"

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("12345".getBytes())
        1 * response.close()
        0 * _

        when:
        result = resource.writeToIfPresent(file)

        then:
        result.readContentLength == 2
        file.text == "hi"

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        1 * response.close()
        0 * _
    }

    def "can apply Transformer to the content of the resource"() {
        def name = new ExternalResourceName("resource")
        def transformer = Mock(Transformer)
        def response = Mock(ExternalResourceReadResponse)

        def resource = repository.resource(name, true)

        when:
        def result = resource.withContentIfPresent(transformer)

        then:
        result.result == "result 1"
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        1 * transformer.transform(_) >> "result 1"
        1 * response.close()
        0 * _

        when:
        result = resource.withContentIfPresent(transformer)

        then:
        result.result == "result 2"
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        1 * transformer.transform(_) >> "result 2"
        1 * response.close()
        0 * _
    }

    def "can apply ContentAction to the content of the resource"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAction)
        def response = Mock(ExternalResourceReadResponse)

        def resource = repository.resource(name, true)
        def metaData = Stub(ExternalResourceMetaData)

        when:
        def result = resource.withContentIfPresent(action)

        then:
        result.result == "result 1"
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        _ * response.metaData >> metaData
        1 * action.execute(_, metaData) >> "result 1"
        1 * response.close()
        0 * _

        when:
        result = resource.withContentIfPresent(action)

        then:
        result.result == "result 2"
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        _ * response.metaData >> metaData
        1 * action.execute(_, metaData) >> "result 2"
        1 * response.close()
        0 * _
    }

    def "closes response when Transformer fails"() {
        def name = new ExternalResourceName("resource")
        def transformer = Mock(Transformer)
        def response = Mock(ExternalResourceReadResponse)

        def resource = repository.resource(name, true)
        def failure = new RuntimeException()

        when:
        resource.withContentIfPresent(transformer)

        then:
        def e = thrown(ResourceException)
        e.message == "Could not get resource 'resource'."
        e.cause == failure

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        1 * transformer.transform(_) >> { throw failure }
        1 * response.close()
        0 * _
    }

    def "closes response when ContentAction fails"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAction)
        def response = Mock(ExternalResourceReadResponse)

        def resource = repository.resource(name, true)
        def metaData = Stub(ExternalResourceMetaData)
        def failure = new RuntimeException()

        when:
        resource.withContentIfPresent(action)

        then:
        def e = thrown(ResourceException)
        e.message == "Could not get resource 'resource'."
        e.cause == failure

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        _ * response.metaData >> metaData
        1 * action.execute(_, metaData) >> { throw failure }
        1 * response.close()
        0 * _
    }

    def "returns null and does not write to file when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def file = tmpDir.file("out")

        def resource = repository.resource(name, true)

        when:
        def result = resource.writeToIfPresent(file)

        then:
        result == null
        !file.exists()

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }

    def "returns null and does not invoke ContentAction when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAction)

        def resource = repository.resource(name, true)

        when:
        def result = resource.withContentIfPresent(action)

        then:
        result == null

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }

    def "returns null and does not invoke Transformer when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def transformer = Mock(Transformer)

        def resource = repository.resource(name, true)

        when:
        def result = resource.withContentIfPresent(transformer)

        then:
        result == null

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }
}
