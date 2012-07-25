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
package org.sakaiproject.nakamura.api.activity;

/**
 * Constants that are used throughout the activity bundle.
 */
public interface ActivityConstants {

  // Request parameters, node property names

  /**
   * The property/parameter name for the application id.
   */
  String PARAM_APPLICATION_ID = "sakai:activity-appid";

  /**
   * The property/parameter name for the activity type.
   */
  String PARAM_ACTIVITY_TYPE = "sakai:activity-type";

  /**
   * The property/parameter name for the activity message.
   */
  String PARAM_ACTIVITY_MESSAGE = "sakai:activityMessage";

  /**
   * The property name for the authorizable who generated the event.
   */
  String PARAM_ACTOR_ID = "sakai:activity-actor";
  /**
   * The property name for the list of authorizables who are affected by this event.
   */
  String PARAM_AUDIENCE_ID = "sakai:activity-audience";
  /**
   * The property name for the source of the activity.
   */
  public static final String PARAM_SOURCE = "sakai:activity-source";
  /**
   * The routes to which this activity is delivered.
   */
  public static final String PARAM_ROUTES = "sakai:routes";

  // Node names

  /**
   * The name for the big store where the original activities will be stored.
   */
  String ACTIVITY_STORE_NAME = "activity";
  /**
   * The name for the big store where the original activities will be copied to.
   */
  String ACTIVITY_FEED_NAME = "activityFeed";

  // Sling:resourceTypes

  /**
   * The sling:resourceType for an activity store.
   * The node with this resourceType will
   * hold the original activity items.
   */
  String ACTIVITY_STORE_RESOURCE_TYPE = "sakai/activityStore";

  /**
   * The sling:resourceType for an activity item.
   */
  String ACTIVITY_ITEM_RESOURCE_TYPE = "sakai/activity";

  /**
   * The sling:resourceType the resource type of the original activity that is posted. Needs to be different from the item that is delivered.
   */
  String ACTIVITY_SOURCE_ITEM_RESOURCE_TYPE = "sakai/activity-post";

  /**
   * resource type for resources that are updated
   */
  String RESOURCE_UPDATE = "sakai/resource-update";

  // Events

  /**
   * OSGi event that gets triggered when an activity occurs.
   */
  String LITE_EVENT_TOPIC = "org/sakaiproject/nakamura/lite/activity";
  /**
   * The property in the event which will hold the location to the original activity.
   */
  String EVENT_PROP_PATH = "sakai:activity-item-path";
  /**
   * Specifies the privacy settings for the activity.
   */
  String PARAM_ACTIVITY_PRIVACY = "sakai:activity-privacy";

  String PRIVACY_PUBLIC = "public";


}
