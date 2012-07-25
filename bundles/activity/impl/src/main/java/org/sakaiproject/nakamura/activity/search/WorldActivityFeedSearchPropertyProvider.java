/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.sakaiproject.nakamura.activity.search;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.activity.ActivityUtils;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.util.PathUtils;

import java.util.Map;

@Component(label = "WorldActivityFeedSearchPropertyProvider")
@Properties({
    @Property(name = "sakai.search.provider", value = "WorldActivityFeed"),
    @Property(name = "sakai.search.resourceType", value = "sakai/page"),
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Provides properties to the activity search templates.")})
@Service
public class WorldActivityFeedSearchPropertyProvider implements SolrSearchPropertyProvider {

  private static final String GROUP_PARAM = "group";

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.search.SearchPropertyProvider#loadUserProperties(org.apache.sling.api.SlingHttpServletRequest,
   *      java.util.Map)
   */
  public void loadUserProperties(SlingHttpServletRequest request,
                                 Map<String, String> propertiesMap) {

    // The current user his feed.
    String user = request.getRemoteUser();

    String path = ActivityUtils.getUserFeed(user);
    // Encode the path
    path = ClientUtils.escapeQueryChars(path);
    propertiesMap.put("_route", path);

    // {!join from=path to=activitysource}readers:anonymous
    if (User.ANON_USER.equals(user)) {
      propertiesMap.put("_activityJoin", "{!join from=path to=activitysource}readers:anonymous");
    } else {
      propertiesMap.put("_activityJoin", "{!join from=path to=activitysource}(readers:anonymous OR readers:everyone)");
    }

    RequestParameter[] rp = request.getRequestParameters(GROUP_PARAM);
    StringBuilder groupQuery = new StringBuilder("(");
    if (rp != null) {
      for (int i = 0; i < rp.length; i++) {
        String groupID = ClientUtils.escapeQueryChars(PathUtils.toUserContentPath("/group/" + rp[i].getString()));
        groupQuery.append("path:").append(groupID).append("/activityFeed");
        if (i < rp.length - 1) {
          groupQuery.append(" OR ");
        }
      }
    }
    groupQuery.append(")");

    propertiesMap.put("_pGroupQuery", groupQuery.toString());
  }

}
