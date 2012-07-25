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
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;

import java.util.Map;

@Component(label = "ActivityTypeSearchPropertyProvider")
@Properties({
    @Property(name = "sakai.search.provider", value = "ActivityType"),
    @Property(name = "sakai.search.resourceType", value = "sakai/page"),
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Provides properties to the activity search templates.")})
@Service
public class ActivityTypeSearchPropertyProvider implements SolrSearchPropertyProvider {

  private static final String TYPE_PARAM = "activityType";

  public void loadUserProperties(SlingHttpServletRequest request, Map<String, String> propertiesMap) {
    RequestParameter[] rp = request.getRequestParameters(TYPE_PARAM);
    StringBuilder typeQuery = new StringBuilder("");
    if (rp != null && rp.length > 0) {
      typeQuery.append(" AND (");
      for (int i = 0; i < rp.length; i++) {
        String type = ClientUtils.escapeQueryChars(rp[i].getString());
        typeQuery.append("activity-type:").append(type);
        if (i < rp.length - 1) {
          typeQuery.append(" OR ");
        }
      }
      typeQuery.append(")");
    }
    propertiesMap.put("_pActivityTypeQuery", typeQuery.toString());
  }

}
