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
package org.sakaiproject.nakamura.activity;

import static org.sakaiproject.nakamura.api.activity.ActivityConstants.ACTIVITY_STORE_NAME;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.sakaiproject.nakamura.activity.routing.ActivityRoute;
import org.sakaiproject.nakamura.activity.routing.ActivityRouterManager;
import org.sakaiproject.nakamura.api.activity.Activity;
import org.sakaiproject.nakamura.api.activity.ActivityConstants;
import org.sakaiproject.nakamura.api.activity.ActivityService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.StoreListener;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.osgi.EventUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

@Component(immediate = true, metatype = true)
@Service
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Event Handler for posting activities from other services.."),
    @Property(name = "event.topics", value = {
        "org/sakaiproject/nakamura/activity/POSTED"})})
public class ActivityServiceImpl implements ActivityService, EventHandler {

  public static final Logger LOGGER = LoggerFactory
      .getLogger(ActivityServiceImpl.class);

  @Reference
  EventAdmin eventAdmin;

  @Reference
  Repository repository;

  @Reference(target = "(osgi.unit.name=org.sakaiproject.nakamura.activity.jpa)")
  EntityManagerFactory entityManagerFactory;

  @Reference
  protected ActivityRouterManager activityRouterManager;

  private static SecureRandom random = null;

  @Override
  public Activity find(String path) {
    EntityManager entityManager = null;
    try {
      entityManager = entityManagerFactory.createEntityManager();
      StringBuilder queryBuilder = new StringBuilder("SELECT x FROM ActivityModel x WHERE x.eid ='");
      queryBuilder.append(StorageClientUtils.getObjectName(path)).append("' AND x.parentPath = '");
      queryBuilder.append(StorageClientUtils.getParentObjectPath(path)).append("'");
      Query query = entityManager.createQuery(queryBuilder.toString());
      List results = query.getResultList();
      if (results != null && !results.isEmpty()) {
        return (ActivityModel) results.get(0);
      }
    } finally {
      closeSilently(entityManager);
    }
    return null;
  }

  @Override
  public void postActivity(String userId, String path, Map<String, Object> attributes) {
    if (attributes == null) {
      throw new IllegalArgumentException("Map of properties cannot be null");
    }
    if (attributes.get(ActivityConstants.PARAM_APPLICATION_ID) == null) {
      throw new IllegalArgumentException("The sakai:activity-appid parameter must not be null");
    }
    if (attributes.get(ActivityConstants.PARAM_ACTIVITY_TYPE) == null) {
      throw new IllegalArgumentException("The sakai:activity-type parameter must not be null");
    }
    Map<String, Object> eventProps = Maps.newHashMap();
    eventProps.put("path", path);
    eventProps.put("userid", userId);
    eventProps.put("attributes", attributes);
    // handleEvent will pick up this event and call back to create the activity
    eventAdmin.postEvent(new Event("org/sakaiproject/nakamura/activity/POSTED", eventProps));
  }

  @Override
  public void handleEvent(Event event) {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      String path = (String) event.getProperty("path");
      String userId = (String) event.getProperty("userid");
      @SuppressWarnings("unchecked")
      final Map<String, Object> activityProperties = (Map<String, Object>) event.getProperty("attributes");

      final ContentManager contentManager = adminSession.getContentManager();
      Content location = contentManager.get(path);
      if (location != null) {
        createActivity(adminSession, location.getPath(), userId, activityProperties);
      }

    } catch (ClientPoolException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (IOException e) {
      LOGGER.warn(e.getMessage(), e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.warn(e.getMessage(), e);
        }
      }
    }
  }

  void createActivity(Session session, String targetPath, String userId, Map<String, Object> activityProperties)
      throws AccessDeniedException, StorageClientException, IOException {
    if (userId == null) {
      userId = session.getUserId();
    }
    if (!userId.equals(session.getUserId()) && !User.ADMIN_USER.equals(session.getUserId())) {
      throw new IllegalStateException("Only Administrative sessions may act on behalf of another user for activities");
    }

    Map<String, Object> props = new HashMap<String, Object>(activityProperties);
    props.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
        ActivityConstants.ACTIVITY_SOURCE_ITEM_RESOURCE_TYPE);
    props.put(ActivityConstants.PARAM_ACTOR_ID, userId);
    props.put(ActivityConstants.PARAM_SOURCE, targetPath);

    // create activity within activityStore
    String storePath = StorageClientUtils.newPath(targetPath, ACTIVITY_STORE_NAME);
    String activityPath = StorageClientUtils.newPath(storePath, createId());
    Activity activity = create(activityPath, userId, props);

    // set permissions
    session.getAccessControlManager().setAcl(
        Security.ZONE_CONTENT,
        storePath,
        new AclModification[]{
            new AclModification(AclModification.denyKey(User.ANON_USER),
                Permissions.ALL.getPermission(), AclModification.Operation.OP_REPLACE),
            new AclModification(AclModification.grantKey(Group.EVERYONE),
                Permissions.CAN_READ.getPermission(), AclModification.Operation.OP_REPLACE),
            new AclModification(AclModification.grantKey(Group.EVERYONE),
                Permissions.CAN_WRITE.getPermission(), AclModification.Operation.OP_REPLACE),
            new AclModification(AclModification.grantKey(userId),
                Permissions.ALL.getPermission(), AclModification.Operation.OP_REPLACE)});

    deliver(activity, session);
  }

  public Activity create(String activityPath, String actorID, Map<String, Object> properties) {
    EntityManager entityManager = null;
    ActivityModel activity = null;
    try {
      entityManager = entityManagerFactory.createEntityManager();
      activity = new ActivityModel(activityPath, new Date(), properties);
      entityManager.getTransaction().begin();
      entityManager.persist(activity);
      entityManager.getTransaction().commit();
      LOGGER.debug("Saved Activity to JPA db: " + activity);

      // post an event that will get picked up by the ActivityIndexingHandler
      final Dictionary<String, String> eventProps = new Hashtable<String, String>();
      eventProps.put(UserConstants.EVENT_PROP_USERID, actorID);
      eventProps.put(ActivityConstants.EVENT_PROP_PATH, activityPath);
      eventProps.put(IndexingHandler.FIELD_PATH, activityPath);
      eventProps.put(IndexingHandler.FIELD_RESOURCE_TYPE, ActivityConstants.ACTIVITY_SOURCE_ITEM_RESOURCE_TYPE);
      EventUtils.sendOsgiEvent(eventProps, StoreListener.DEFAULT_CREATE_TOPIC, eventAdmin);

    } finally {
      closeSilently(entityManager);
    }
    return activity;
  }

  /**
   * @return Creates a unique path to an activity in the form of 2010-01-21-09-randombit
   */
  static String createId() {
    Calendar c = Calendar.getInstance();

    String[] vals = new String[4];
    vals[0] = "" + c.get(Calendar.YEAR);
    vals[1] = StringUtils.leftPad("" + (c.get(Calendar.MONTH) + 1), 2, "0");
    vals[2] = StringUtils.leftPad("" + c.get(Calendar.DAY_OF_MONTH), 2, "0");
    vals[3] = StringUtils.leftPad("" + c.get(Calendar.HOUR_OF_DAY), 2, "0");

    StringBuilder id = new StringBuilder();

    for (String v : vals) {
      id.append(v).append("-");
    }

    byte[] bytes = new byte[20];
    String randomHash = "";
    try {
      if (random == null) {
        random = SecureRandom.getInstance("SHA1PRNG");
      }
      random.nextBytes(bytes);
      randomHash = Arrays.toString(bytes);
      randomHash = org.sakaiproject.nakamura.util.StringUtils
          .sha1Hash(randomHash);
    } catch (NoSuchAlgorithmException e) {
      LOGGER.error("No SHA algorithm on system?", e);
    } catch (UnsupportedEncodingException e) {
      LOGGER.error("Byte encoding not supported?", e);
    }

    id.append(randomHash);
    return id.toString();
  }

  private void closeSilently(EntityManager em) {
    if (em != null) {
      try {
        em.close();
      } catch (Throwable t) {
        LOGGER.warn("Error closing EntityManager", t);
      }
    }
  }

  private void deliver(Activity activity, Session adminSession) {
    if (activity == null || activity.getActor() == null) {
      // we must know the actor
      throw new IllegalStateException(
          "Could not determine actor of activity: " + activity);
    }

    // Get all the routes for this activity.
    List<ActivityRoute> routes = activityRouterManager
        .getActivityRoutes(activity.toContent(), adminSession);

    // Copy the activity items to each endpoint.
    for (ActivityRoute route : routes) {
      deliverActivityToFeed(activity, route.getDestination());
    }

  }

  private void deliverActivityToFeed(Activity activity,
                                     String activityFeedPath) {
    // ensure the activityFeed node with the proper type
    String deliveryPath = StorageClientUtils
        .newPath(activityFeedPath, activity.getEid());
    ImmutableMap.Builder<String, Object> contentProperties = ImmutableMap.builder();
    for (Map.Entry<String, Object> e : activity.toContent().getProperties().entrySet()) {
      if (!JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY.equals(e.getKey())) {
        contentProperties.put(e.getKey(), e.getValue());
      }
    }
    contentProperties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
        ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE);

    create(deliveryPath, activity.getActor(), contentProperties.build());
    LOGGER.debug("Delivered an activity: " + activity);
  }

}

