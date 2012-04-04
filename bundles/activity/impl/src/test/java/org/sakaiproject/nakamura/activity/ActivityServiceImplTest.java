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

package org.sakaiproject.nakamura.activity;

import com.google.common.collect.ImmutableMap;
import junit.framework.Assert;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.activity.ActivityConstants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

public class ActivityServiceImplTest extends Assert {

  ActivityServiceImpl activityService;

  private Repository repository;

  private ActivityServiceCallback activityServiceCallback;

  @Before
  public void setup() throws Exception {
    this.activityService = new ActivityServiceImpl();
    this.activityService.eventAdmin = Mockito.mock(EventAdmin.class);
    repository = new BaseMemoryRepository().getRepository();
    activityServiceCallback = Mockito.mock(ActivityServiceCallback.class);

    final Session adminSession = repository.loginAdministrative();
    adminSession.getAuthorizableManager().createUser("joe", "joe", "joe",
        ImmutableMap.of("name", (Object) "joe"));
    adminSession.logout();

  }

  @Test
  public void testCreateActivityAsAdmin() throws Exception {
    final Session adminSession = repository.loginAdministrative();
    final Session userSession = repository.login("joe", "joe");
    final Session anonSession = repository.login();

    Content content = new Content("/some/arbitrary/path", ImmutableMap.of("foo", (Object) "bar"));
    String userID = "alice";

    this.activityService.createActivity(adminSession, content, userID, activityServiceCallback);

    Mockito.verify(this.activityService.eventAdmin).postEvent(Mockito.any(Event.class));
    Mockito.verify(activityServiceCallback).processRequest(Mockito.any(Content.class));

    // make sure activity store got created
    String storePath = "/some/arbitrary/path/" + ActivityConstants.ACTIVITY_STORE_NAME;
    Content store = adminSession.getContentManager().get(storePath);
    Assert.assertNotNull(store);
    Assert.assertEquals(ActivityConstants.ACTIVITY_STORE_RESOURCE_TYPE,
        store.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY));

    // check permissions of activity store    
    adminSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
        Permissions.CAN_ANYTHING);
    userSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
        Permissions.CAN_READ);
    userSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
        Permissions.CAN_WRITE);
    boolean canDelete = false;
    try {
      userSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
          Permissions.CAN_DELETE);
      canDelete = true;
    } catch (AccessDeniedException expected) {
    }
    Assert.assertFalse(canDelete);

    boolean canRead = false;
    try {
      anonSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
          Permissions.CAN_READ);
      canRead = true;
    } catch (AccessDeniedException expected) {
    }
    Assert.assertFalse(canRead);

    boolean canWrite = false;
    try {
      anonSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
          Permissions.CAN_WRITE);
      canWrite = true;
    } catch (AccessDeniedException expected) {
    }
    Assert.assertFalse(canWrite);

    // make sure activity feed got created
    Content feed = adminSession.getContentManager().get("/some/arbitrary/path/activityFeed");
    Assert.assertNotNull(feed);

    // make sure at least one activity node exists under the activity store
    boolean activityFound = false;
    for (Content item : store.listChildren()) {
      if (item.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).equals
          (ActivityConstants.ACTIVITY_SOURCE_ITEM_RESOURCE_TYPE)) {
        activityFound = true;
        Assert.assertEquals("alice", item.getProperty(ActivityConstants.PARAM_ACTOR_ID));
        Assert.assertEquals("/some/arbitrary/path", item.getProperty(ActivityConstants.PARAM_SOURCE));
      }
    }
    Assert.assertTrue(activityFound);

    adminSession.logout();
    userSession.logout();
  }

  @Test(expected = IllegalStateException.class)
  public void testCreateActivityAsNonAdminActingAsAnotherUser() throws Exception {
    // make sure a non-admin can't create an activity on behalf of another.
    final Session session = repository.login("joe", "joe");
    Content content = new Content("/some/arbitrary/path", ImmutableMap.of("foo", (Object) "bar"));
    String userID = "alice";
    this.activityService.createActivity(session, content, userID, activityServiceCallback);
  }

}
