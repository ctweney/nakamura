/**
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.activity.ActivityConstants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.AllResourceTypeIndexingHandler;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.ResourceIndexingService;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.util.SparseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexing handler for activities.
 */
@Component(immediate = true)
@Service(AllResourceTypeIndexingHandler.class)
public class ActivityIndexingHandler implements IndexingHandler, AllResourceTypeIndexingHandler {

  // list of properties to be indexed
  private static final Set<String> WHITELISTED_PROPS = ImmutableSet.of("_created");

  private static final Logger LOGGER = LoggerFactory
      .getLogger(ActivityIndexingHandler.class);

  private static final Set<String> CONTENT_TYPES = Sets.newHashSet(
      ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE,
      ActivityConstants.ACTIVITY_SOURCE_ITEM_RESOURCE_TYPE);

  private static final String PROP_ACTIVITY_SOURCE = "activitysource";

  private static final int ROWS_PER_SEARCH = 5000;

  @Reference
  Repository repository;

  @Reference(target = "(type=sparse)")
  private ResourceIndexingService resourceIndexingService;

  @Reference
  private SolrServerService solrSearchService;

  @Activate
  public void activate(Map<String, Object> properties) throws Exception {
    for (String type : CONTENT_TYPES) {
      resourceIndexingService.addHandler(type, this);
    }
  }

  @Deactivate
  public void deactivate(Map<String, Object> properties) {
    for (String type : CONTENT_TYPES) {
      resourceIndexingService.removeHandler(type, this);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDocuments(org.sakaiproject.nakamura.api.solr.RepositorySession,
   *      org.osgi.service.event.Event)
   */
  public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession,
      Event event) {
    String path = (String) event.getProperty(FIELD_PATH);

    List<SolrInputDocument> documents = Lists.newArrayList();
    if (!StringUtils.isBlank(path)) {
      try {
        Session session = repositorySession.adaptTo(Session.class);
        ContentManager cm = session.getContentManager();
        Content content = cm.get(path);

        if (content != null) {
          if (isActivity(content)) {
            SolrInputDocument doc = convertToSolrDoc(content);
            documents.add(doc);
          } else {
            // this isn't an activity, but there may be children that are.
            checkChildNodes(content, documents, session);
          }
        }
      } catch (StorageClientException e) {
        LOGGER.warn(e.getMessage(), e);
      } catch (AccessDeniedException e) {
        LOGGER.warn(e.getMessage(), e);
      } catch (SolrServerException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    LOGGER.debug("Got documents {} ", documents);
    return documents;
  }

  private SolrInputDocument convertToSolrDoc(Content content) throws StorageClientException, AccessDeniedException {
    SolrInputDocument doc = new SolrInputDocument();
    for (String prop : WHITELISTED_PROPS) {
      doc.addField(prop, content.getProperty(prop));
    }
    doc.addField(_DOC_SOURCE_OBJECT, content);
    doc.addField(PROP_ACTIVITY_SOURCE, content.getProperty(ActivityConstants.PARAM_SOURCE));
    String[] routes = findRoutes(content.getPath());
    if (routes != null) {
      doc.addField("routes", routes);
    }
    return doc;
  }

  private boolean isActivity(Content content) {
    return CONTENT_TYPES.contains(content.getProperty("sling:resourceType"));
  }

  private void checkChildNodes(Content content, List<SolrInputDocument> documents, Session session)
      throws SolrServerException, StorageClientException, AccessDeniedException {
    LOGGER.debug("Checking under path " + content.getPath() + " for child nodes that are sakai/activity");
    int start = 0;
    while (true) {
      SolrDocumentList activities = getMoreActivities(content, start);
      for (SolrDocument activity : activities) {
        Object path = activity.getFirstValue("path");
        if (path != null && path instanceof String) {
          ContentManager cm = session.getContentManager();
          Content activityContent = cm.get((String) path);
          if ( activityContent != null && isActivity(activityContent)) {
            LOGGER.debug(activityContent.getPath() + " is a sakai/activity child node, adding to list of indexed docs");
            SolrInputDocument doc = convertToSolrDoc(activityContent);
            documents.add(doc);
          }
        }
      }
      if (activities.getNumFound() > (activities.getStart() + ROWS_PER_SEARCH)) {
        start = start + ROWS_PER_SEARCH;
      } else {
        break;
      }
    }
  }

  private String[] findRoutes(String activityPath) throws StorageClientException, AccessDeniedException {
    String[] routes = null;
    Session adminSession = repository.loginAdministrative();
    try {
      ContentManager adminCm = adminSession.getContentManager();
      Content routesNode = adminCm.get(StorageClientUtils.newPath(activityPath, ActivityConstants.PARAM_ROUTES));
      if (routesNode != null) {
        routes = (String[]) routesNode.getProperty(ActivityConstants.PARAM_ROUTES);
      }
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
    return routes;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDeleteQueries(org.sakaiproject.nakamura.api.solr.RepositorySession,
   *      org.osgi.service.event.Event)
   */
  public Collection<String> getDeleteQueries(RepositorySession repositorySession,
      Event event) {
    List<String> retval = Collections.emptyList();
    LOGGER.debug("GetDelete for {} ", event);
    String path = (String) event.getProperty(FIELD_PATH);
    String resourceType = (String) event.getProperty("resourceType");
    if (CONTENT_TYPES.contains(resourceType)) {
      retval = ImmutableList.of("id:" + ClientUtils.escapeQueryChars(path));
    }
    return retval;
  }

  private SolrDocumentList getMoreActivities(Content content, int start) throws SolrServerException {
    SolrQuery query = new SolrQuery("resourceType:" +
        ClientUtils.escapeQueryChars(ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE) + " AND path:" +
        ClientUtils.escapeQueryChars(content.getPath()));
    query.setFields("path"); // we only need the path field
    query.setRows(ROWS_PER_SEARCH);
    query.setStart(start);
    // go direct to solr server so we can get all documents more easily
    QueryResponse solrResponse = this.solrSearchService.getServer().query(query);
    LOGGER.debug("Got " + solrResponse.getResults().getNumFound() + " activities from solr index; " +
        "this batch starts at " + solrResponse.getResults().getStart());
    return solrResponse.getResults();
  }

}
