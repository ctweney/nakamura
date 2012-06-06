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

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;

import java.sql.Driver;
import java.util.Hashtable;
import java.util.Map;
import javax.sql.DataSource;

@Component(immediate = true, metatype = true)
@Properties(value = {
    @Property(name = DataSourceProvider.DRIVER, value = DataSourceProvider.DEFAULT_DRIVER),
    @Property(name = DataSourceProvider.URL, value = DataSourceProvider.DEFAULT_URL),
    @Property(name = DataSourceProvider.USERNAME, value = DataSourceProvider.DEFAULT_USERNAME),
    @Property(name = DataSourceProvider.PASSWORD, value = DataSourceProvider.DEFAULT_PASSWORD),
    @Property(name = DataSourceProvider.DATA_SOURCE_NAME, value = DataSourceProvider.DEFAULT_DATA_SOURCE_NAME)
})
public class DataSourceProvider {

  public static final String DEFAULT_DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
  public static final String DEFAULT_URL = "jdbc:derby:sling/nakamura.openjpa/db;create=true";
  public static final String DEFAULT_USERNAME = "sakai";
  public static final String DEFAULT_PASSWORD = "ironchef";
  public static final String DEFAULT_DATA_SOURCE_NAME = "nakamura.openjpa";

  public static final String DRIVER = "driver";
  public static final String URL = "url";
  public static final String USERNAME = "username";
  public static final String PASSWORD = "password";
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

    BundleContext bndCtx = ctx.getBundleContext();

    // load up the requested driver
    Class<Driver> driver = bndCtx.getBundle().loadClass(driverName);

    DriverDataSource dataSource = new DriverDataSource(driver, url, username, password);

    Hashtable<String, String> serviceProps = new Hashtable<String, String>();
    serviceProps.put(DATA_SOURCE_NAME, dataSourceName);

    serviceReg = bndCtx.registerService(DataSource.class.getName(), dataSource, serviceProps);
  }

  @Deactivate
  public void deactivate(ComponentContext ctx) {
    serviceReg.unregister();
  }
}
