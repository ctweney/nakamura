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
package org.sakaiproject.nakamura.user.search;

import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.GroupParams;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.SearchUtil;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Service({
    SolrSearchPropertyProvider.class,
    SolrSearchResultProcessor.class
})
@Properties({
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value="UsersSearchQueryHandler"),
    @Property(name = SearchConstants.REG_PROCESSOR_NAMES, value = "UsersSearchQueryHandler")
})
public class UsersSearchQueryHandler implements SolrSearchPropertyProvider, SolrSearchResultProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(UsersSearchQueryHandler.class);
  private static final String Q_FORMAT = "name:(%s) OR firstName:(%<s) OR lastName:(%<s) OR email:(%<s) OR ngram:(%<s) OR edgengram:(%<s)";

  public enum REQUEST_PARAMS {
    fullprofile,
    q,
    tags
  }

  /**
   * For now, we delegate JSON handling to the Authorizable-result writer which currently
   * resides in the Presence module. However, after refactoring that processor's Presence and
   * Connections dependencies into their own result-writing services, we should finally be
   * able to relocate its logic to this module.
   */
  @Reference(target = "(sakai.search.processor=Profile)")
  SolrSearchResultProcessor profileNodeSearchResultProcessor;

  @Override
  public void loadUserProperties(SlingHttpServletRequest request, Map<String, String> parametersMap) {
    StringBuilder qBuilder = new StringBuilder();

    // Most of the request parameters affect SolrQuery options, and therefore cannot be handled
    // solely within the basic query string itself. The one exception is the standard "tag"
    // clause.
    String tagsParam = getSearchParam(parametersMap, REQUEST_PARAMS.tags.toString());
    if (tagsParam != null) {
      qBuilder.append("tag:(").append(tagsParam).append(")");
    }

    // Did the client specify a text search?
    String qParam = getSearchParam(parametersMap, REQUEST_PARAMS.q.toString());
    if (qParam != null) {
      if (qBuilder.length() > 0) {
        qBuilder.append(" AND ");
      }
      qBuilder.append("(");
      (new Formatter(qBuilder)).format(Q_FORMAT, qParam);
      if (isFullProfile(parametersMap)) {
        qBuilder.append(" OR profile:(").append(qParam).append(")");
      }
      qBuilder.append(")");
    } else if (qBuilder.length() == 0) {
      qBuilder.append(getDefaultQueryString(parametersMap));
    }

    parametersMap.put("_q", qBuilder.toString());
  }

  @Override
  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {
    LOGGER.info("Input Query configuration = {}", query);
    Map<String, String> parametersMap = loadParametersMap(request);
    queryConfigure(parametersMap, query);
    return profileNodeSearchResultProcessor.getSearchResultSet(request, query);
  }

  @Override
  public void writeResult(SlingHttpServletRequest request, JSONWriter write, Result result) throws JSONException {
    profileNodeSearchResultProcessor.writeResult(request, write, result);
  }

  private void queryConfigure(Map<String, String> parametersMap, Query query) {
    Map<String, Object> queryOptions = query.getOptions();

    // Configure the Filter Queries.
    Set<String> filterQueries = new HashSet<String>();
    filterQueries.add(getResourceTypeClause(parametersMap));
    queryOptions.put(CommonParams.FQ, filterQueries);

    // If both Authorizable and Profile records will be searched, collapse them
    // into a single result for a single person.
    if (isFullProfile(parametersMap)) {
      queryOptions.put(GroupParams.GROUP, Boolean.TRUE);
      queryOptions.put(GroupParams.GROUP_FIELD, "returnpath");
      queryOptions.put(GroupParams.GROUP_TOTAL_COUNT, Boolean.TRUE);
    }
  }

  private String getDefaultQueryString(Map<String, String> parametersMap) {
    // Before 4.0, the usual "*:*" pass-through would incur unexpected cost.
    // As of 4.0, filter queries are processed in parallel with the main query
    // and the performance profiles should be closer.
    return getResourceTypeClause(parametersMap);
  }

  private String getResourceTypeClause(Map<String, String> parametersMap) {
    // fq=type:u&fq=resourceType:(authorizable OR profile)
    StringBuilder sb = new StringBuilder("type:u AND resourceType:(authorizable");
    if (isFullProfile(parametersMap)) {
      sb.append(" OR profile");
    }
    sb.append(")");
    return sb.toString();
  }

  private String getSearchParam(Map<String, String> parametersMap, String key) {
    String param = parametersMap.get(key);
    if (StringUtils.isBlank(param) || "*".equals(StringUtils.strip(param))) {
      return null;
    } else {
      return param;
    }
  }

  /**
   * Even if a full profile search has been requested, there is no need to
   * include Profile records unless the client specified a text search.
   * A full wildcard search can be confined to Authorizable records.
   */
  private boolean isFullProfile(Map<String, String> parametersMap) {
    return ("true".equals(parametersMap.get(REQUEST_PARAMS.fullprofile.toString())) &&
        (getSearchParam(parametersMap, REQUEST_PARAMS.q.toString()) != null));
  }

  /**
   * TODO Refactor out of SolrSearchServlet for re-use.
   */
  private static Map<String, String> loadParametersMap(SlingHttpServletRequest request) {
    Map<String, String> propertiesMap = new HashMap<String, String>();

    // 0. load authorizable (user) information
    String userId = request.getRemoteUser();
    propertiesMap.put("_userId", ClientUtils.escapeQueryChars(userId));

    // 2. load in properties from the request
    RequestParameterMap params = request.getRequestParameterMap();
    for (Map.Entry<String, RequestParameter[]> entry : params.entrySet()) {
      RequestParameter[] vals = entry.getValue();
      String requestValue = vals[0].getString();
      if (StringUtils.isBlank(requestValue)) {
        continue;
      }
      String key = entry.getKey();
      String val = SearchUtil.escapeString(requestValue, Query.SOLR);
      propertiesMap.put(key, val);
    }
    return propertiesMap;
  }
}
