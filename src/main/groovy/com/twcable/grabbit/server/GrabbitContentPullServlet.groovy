/*
 * Copyright 2015 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twcable.grabbit.server

import com.twcable.grabbit.server.services.ServerService
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import org.apache.felix.scr.annotations.Reference
import org.apache.felix.scr.annotations.sling.SlingServlet
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.servlets.SlingAllMethodsServlet

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import static javax.servlet.http.HttpServletResponse.SC_OK

/**
 * This servlet is used to pull a stream of Grabbit content.
 *
 * A client request is made via {@link com.twcable.grabbit.client.batch.steps.http.CreateHttpConnectionTasklet} to
 * start the stream of content.
 */
@Slf4j
@CompileStatic
@SlingServlet(methods = ['POST'], resourceTypes = ['twcable:grabbit/content'])
class GrabbitContentPullServlet extends SlingAllMethodsServlet {

    @Reference(bind = 'setServerService')
    ServerService serverService

    /**
     * This POST request starts a stream of Grabbit content. The servlet looks for several query parameters related
     * to a stream.
     *
     * <ul>
     *     <li><b>path</b> is the path to the content on the server to be streamed. This is required.
     *     <li><b>excludePath</b> is a sub-path to exclude from the stream. This can have multiple values. It is not required.
     *     <li><b>after</b> is ISO-8601 date that is used to stream delta content. It is not required.
     * </ul>
     *
     * {@link GrabbitContentPullServlet} will use the request remote user credentials to authenticate against the server JCR.
     *
     * @param request The request to process.
     * @param response Our response to the request.
     */
    @Override
    void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        log.trace "Received a post method"

        try {
            StringBuilder sb = new StringBuilder();
            String s;
            while ((s = request.getReader().readLine()) != null) {
                sb.append(s);
            }

            def jsonSlurper = new JsonSlurper()
            Map jsonObject = null

            try {
                jsonObject = (Map)jsonSlurper.parseText(sb.toString())
            } catch (Exception e) {
                log.error("Exception occurred trying to read string into json object", e);
            }

            log.info "jsonObject={}", jsonObject

            if (!jsonObject.path) {
                log.info "jsonObject doesn't contain path"
                response.status = SC_BAD_REQUEST
                response.writer.write("No path provided for content!")
                return
            } else {
                log.info "jsonObject.path=" + jsonObject.path
            }

            final String path = jsonObject.path

            log.debug "path={}", path

            String afterDateString = "";
            if (jsonObject.after) {
                afterDateString = jsonObject.after
            }
            log.debug "afterDateString={}", afterDateString

            List<String> excludePathsList = new ArrayList<>();
            if (jsonObject.excludePaths) {
                excludePathsList = (List<String>) jsonObject.excludePaths
            }
            log.debug "excludePathsList={}", StringUtils.join(excludePathsList, ",")

            response.contentType = "application/octet-stream"
            response.status = SC_OK

            //The Login of the user making this request.
            //This user will be used to connect to JCR.
            //If the User is null, 'anonymous' will be used to connect to JCR.
            final serverUsername = request.remoteUser

            log.info "\n\n *** About to call server service with the following to start pushing content to " +
                    "client***\n\n" +
                    "path={}\nafter={}\nexcludePathList={}\n\n\n", path,
                    afterDateString, StringUtils.join(excludePathsList, ",")

            serverService.getContentForRootPath(serverUsername, path, excludePathsList ?: null,
                    afterDateString ?: null, response.outputStream)
        } catch (Exception e) {
            log.error "Exception occurred processing the request", e;
        }
    }
}
