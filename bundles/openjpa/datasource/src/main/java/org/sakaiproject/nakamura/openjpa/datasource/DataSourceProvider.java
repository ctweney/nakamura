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

package org.sakaiproject.nakamura.openjpa.datasource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;

import java.util.Hashtable;
import java.util.Map;
import javax.sql.DataSource;

@Component(immediate = true, metatype = true)
public class DataSourceProvider {

  public static final String DEFAULT_DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
  public static final String DEFAULT_URL = "jdbc:derby:sling/nakamura-openjpa/db;create=true";
  public static final String DEFAULT_USERNAME = "";
  public static final String DEFAULT_PASSWORD = "";
  public static final String DEFAULT_DATA_SOURCE_NAME = "nakamura-openjpa";

  @Property(value = DEFAULT_DRIVER)
  public static final String DRIVER = "driver";

  @Property(value = DEFAULT_URL)
  public static final String URL = "url";

  @Property(value = DEFAULT_USERNAME)
  public static final String USERNAME = "username";

  @Property(value = DEFAULT_PASSWORD)
  public static final String PASSWORD = "password";

  @Property(value = DEFAULT_DATA_SOURCE_NAME)
  public static final String DATA_SOURCE_NAME = "dataSourceName";

  private ServiceRegistration serviceReg;

  @Activate
  public void activate(ComponentContext ctx, Map<?, ?> props) throws Exception {
    String driverName = PropertiesUtil.toString(props.get(DRIVER), DEFAULT_DRIVER);
    String dataSourceName = PropertiesUtil.toString(props.get(DATA_SOURCE_NAME), DEFAULT_DATA_SOURCE_NAME);
    String url = PropertiesUtil.toString(props.get(URL), DEFAULT_URL);
    String username = PropertiesUtil.toString(props.get(USERNAME), DEFAULT_USERNAME);
    String password = PropertiesUtil.toString(props.get(PASSWORD), DEFAULT_PASSWORD);

    if (StringUtils.isBlank(driverName) || StringUtils.isBlank(url)
        || StringUtils.isBlank(dataSourceName)) {
      throw new IllegalArgumentException("Must configure driver, url and dataSourceName");
    }

    BasicDataSource dataSource = new BasicDataSource();
    dataSource.setDriverClassName(driverName);
    dataSource.setUrl(url);
    dataSource.setUsername(username);
    dataSource.setPassword(password);

    Hashtable<String, String> serviceProps = new Hashtable<String, String>();
    serviceProps.put(DATA_SOURCE_NAME, dataSourceName);

    serviceReg = ctx.getBundleContext().registerService(DataSource.class.getName(), dataSource, serviceProps);
  }

  @Deactivate
  public void deactivate(ComponentContext ctx) {
    serviceReg.unregister();
  }
}
