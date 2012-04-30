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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.activity.ActivityConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ActivityManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.ResourceIndexingService;
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
public class ActivityIndexingHandler implements IndexingHandler {

  // map of properties to be indexed
  private static final Map<String, String> WHITELISTED_PROPS;

  static {
    ImmutableMap.Builder<String, String> propBuilder = ImmutableMap.builder();
    propBuilder.put("_created", "_created");
    propBuilder.put(ActivityConstants.PARAM_ACTIVITY_TYPE, "activity-type");
    propBuilder.put(ActivityConstants.PARAM_ACTIVITY_MESSAGE, "activityMessage");
    WHITELISTED_PROPS = propBuilder.build();
  }

  private static final Logger logger = LoggerFactory
      .getLogger(ActivityIndexingHandler.class);

  private static final Set<String> CONTENT_TYPES = Sets.newHashSet(
      ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE,
      ActivityConstants.ACTIVITY_SOURCE_ITEM_RESOURCE_TYPE);

  @Reference(target = "(type=sparse-activity)")
  private ResourceIndexingService resourceIndexingService;

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
        ActivityManager activityManager = session.getActivityManager();
        Content content = activityManager.get(path);

        if (content != null) {
          if (!CONTENT_TYPES.contains(content.getProperty("sling:resourceType"))) {
            return documents;
          }
          SolrInputDocument doc = new SolrInputDocument();
          for (String prop : WHITELISTED_PROPS.keySet()) {
            Object value = content.getProperty(prop);
            if (value != null) {
              doc.addField(WHITELISTED_PROPS.get(prop), value);
            }
          }
          doc.addField(_DOC_SOURCE_OBJECT, content);
          documents.add(doc);
        }
      } catch (StorageClientException e) {
        logger.warn(e.getMessage(), e);
      } catch (AccessDeniedException e) {
        logger.warn(e.getMessage(), e);
      }
    }
    logger.debug("Got documents {} ", documents);
    return documents;
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
    logger.debug("GetDelete for {} ", event);
    String path = (String) event.getProperty(FIELD_PATH);
    String resourceType = (String) event.getProperty("resourceType");
    if (CONTENT_TYPES.contains(resourceType)) {
      retval = ImmutableList.of("id:" + ClientUtils.escapeQueryChars(path));
    }
    return retval;
  }
}
