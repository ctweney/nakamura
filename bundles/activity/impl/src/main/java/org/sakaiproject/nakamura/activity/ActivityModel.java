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

import org.apache.commons.io.IOUtils;
import org.apache.openjpa.persistence.jdbc.Index;
import org.sakaiproject.nakamura.api.activity.Activity;
import org.sakaiproject.nakamura.api.activity.ActivityConstants;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Transient;
import javax.persistence.Version;

@Entity
@Access(value = AccessType.FIELD)
public class ActivityModel implements Activity, Serializable {

  private static final long serialVersionUID = 4417073305813161429L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @Index
  private String eid;

  @Index
  private String parentPath;

  private String type;

  private String message;

  private Date occurrenceDate;

  private String actor;

  @Transient
  private byte[] extraPropertiesBinary;

  @Transient
  private Map<String, Serializable> extraProperties;

  @Version
  private int version;

  public ActivityModel() {
    // JPA framework requires a no-arg constructor
  }

  /**
   * Content constructor for compatibility with Nakamura content.
   *
   * @param content Map of properties.
   */
  public ActivityModel(String path, Date occurrenceDate, Map<String, Object> content) {
    this.parentPath = StorageClientUtils.getParentObjectPath(path);
    this.eid = StorageClientUtils.getObjectName(path);
    this.occurrenceDate = occurrenceDate;

    if (content != null) {
      // extract the top-level and "extra" properties
      for (String key : content.keySet()) {
        if (ActivityConstants.PARAM_ACTIVITY_TYPE.equals(key)) {
          this.type = (String) content.get(key);
        } else if (ActivityConstants.PARAM_ACTIVITY_MESSAGE.equals(key)) {
          this.message = (String) content.get(key);
        } else if (ActivityConstants.PARAM_ACTOR_ID.equals(key)) {
          this.actor = (String) content.get(key);
        } else {
          if (extraProperties == null) {
            extraProperties = new HashMap<String, Serializable>();
          }
          extraProperties.put(key, (Serializable) content.get(key));
        }
      }
    }
  }

  /**
   * @return The internally generated ID of this activity.
   */
  public long getId() {
    return id;
  }

  /**
   * @return The externally generated ID.
   */
  public String getEid() {
    return eid;
  }

  public void setEid(String eid) {
    this.eid = eid;
  }

  public String getParentPath() {
    return parentPath;
  }

  public void setParentPath(String parentPath) {
    this.parentPath = parentPath;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Date getOccurrenceDate() {
    return occurrenceDate;
  }

  public void setOccurrenceDate(Date occurred) {
    this.occurrenceDate = occurred;
  }

  public String getActor() {
    return actor;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  @Access(value = AccessType.PROPERTY)
  protected byte[] getExtraPropertiesBinary() {
    serializeExtraProperties();
    return extraPropertiesBinary;
  }

  protected void setExtraPropertiesBinary(byte[] extraPropertiesBinary) {
    this.extraPropertiesBinary = extraPropertiesBinary;
    unserializeExtraProperties();
  }

  @Transient
  public Map<String, Serializable> getExtraProperties() {
    return extraProperties;
  }

  public void setExtraProperties(Map<String, Serializable> extraProperties) {
    this.extraProperties = extraProperties;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  @Transient
  public Content toContent() {
    HashMap<String, Object> properties = new HashMap<String, Object>();
    if (getExtraProperties() != null) {
      properties.putAll(getExtraProperties());
    }
    if (getType() != null) {
      properties.put(ActivityConstants.PARAM_ACTIVITY_TYPE, getType());
    }
    if (getMessage() != null) {
      properties.put(ActivityConstants.PARAM_ACTIVITY_MESSAGE, getMessage());
    }
    if (getActor() != null) {
      properties.put(ActivityConstants.PARAM_ACTOR_ID, getActor());
    }
    if ( getOccurrenceDate() != null ) {
      properties.put("_created", getOccurrenceDate().getTime());
    }
    String path = StorageClientUtils.newPath(getParentPath(), getEid());
    return new Content(path, properties);
  }

  private void serializeExtraProperties() {
    if ( extraProperties == null || extraProperties.isEmpty()) {
      return;
    }
    ByteArrayOutputStream baos = null;
    ObjectOutputStream oos = null;
    try {
      baos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(baos);
      oos.writeObject(extraProperties);
      extraPropertiesBinary = baos.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize extra properties.", e);
    } finally {
      IOUtils.closeQuietly(baos);
      IOUtils.closeQuietly(oos);
    }
  }

  @SuppressWarnings("unchecked")
  private void unserializeExtraProperties() {
    if ( extraPropertiesBinary == null ) {
      return;
    }
    ByteArrayInputStream bais = null;
    ObjectInputStream ois = null;
    try {
      bais = new ByteArrayInputStream(extraPropertiesBinary);
      ois = new ObjectInputStream(bais);
      extraProperties = (Map<String, Serializable>) ois.readObject();
    } catch (IOException e) {
      throw new RuntimeException("Failed to unserialize extra properties.", e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Failed to unserialize extra properties.", e);
    } finally {
      IOUtils.closeQuietly(bais);
      IOUtils.closeQuietly(ois);
    }
  }

  @Override
  public String toString() {
    return "Activity{" +
        "id=" + getId() +
        ", eid='" + getEid() + '\'' +
        ", actor='" + getActor() + '\'' +
        ", parentPath='" + getParentPath() + '\'' +
        ", type='" + getType() + '\'' +
        ", message='" + getMessage() + '\'' +
        ", occurrenceDate=" + getOccurrenceDate() +
        ", extraProperties=" + getExtraProperties() +
        ", version=" + getVersion() +
        '}';
  }

}
