package org.nutz.boot.starter.seata.datasource;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.nutz.boot.starter.seata.SeataHelper;
import org.nutz.boot.starter.seata.SeataStarter;
import org.nutz.ioc.IocEventListener;
import org.nutz.ioc.impl.PropertiesProxy;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.log.Log;
import org.nutz.log.Logs;

import com.alibaba.druid.pool.DruidDataSource;

import io.seata.rm.DefaultResourceManager;
import io.seata.rm.datasource.DataSourceProxy;

@IocBean
public class SeataDataSourceHandler implements IocEventListener {
    
    private static final Log log = Logs.get();
    
    @Inject
    protected PropertiesProxy conf;

    public Object afterBorn(Object obj, String beanName) {
        return obj;
    }

    public Object afterCreate(Object obj, String beanName) {
        if (obj == null)
            return null;
        if (obj instanceof DruidDataSource) {
            if (SeataHelper.disableGlobalTransaction) {
                if (log.isInfoEnabled()) {
                    log.info("Global transaction is disabled.");
                }
                return obj;
            }
            log.info("proxy DruidDataSource : " + obj.hashCode());
            DruidDataSource ds = (DruidDataSource)obj;
            DataSourceProxy proxy = new DataSourceProxy2(ds, "DEFAULT");
            DefaultResourceManager.get().registerResource(proxy);
            if (conf.getBoolean(SeataStarter.PROP_CREATE_UNDO, true)) {
                try (Connection conn = ds.getConnection()) {
                    Statement st = conn.createStatement();
                    st.execute("CREATE TABLE IF NOT EXISTS " + "`undo_log` (\r\n" + 
                            "  `id` bigint(20) NOT NULL AUTO_INCREMENT,\r\n" + 
                            "  `branch_id` bigint(20) NOT NULL,\r\n" + 
                            "  `xid` varchar(100) NOT NULL,\r\n" + 
                            "  `rollback_info` longblob NOT NULL,\r\n" + 
                            "  `log_status` int(11) NOT NULL,\r\n" + 
                            "  `log_created` datetime NOT NULL,\r\n" + 
                            "  `log_modified` datetime NOT NULL,\r\n" + 
                            "  `ext` varchar(100) DEFAULT NULL,\r\n" + 
                            "  PRIMARY KEY (`id`),\r\n" + 
                            "  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)\r\n" + 
                            ") ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8");
                    st.close();
                }
                catch (Throwable e) {
                    log.warn("fail to create fescar's undo_log table", e);
                }
            }
            return proxy;
        }
        return obj;
    }

    public int getOrder() {
        return 0;
    }

    static class DataSourceProxy2 extends DataSourceProxy implements Closeable {
        
        public DataSourceProxy2(DataSource targetDataSource) {
            super(targetDataSource);
        }

        public DataSourceProxy2(DataSource targetDataSource, String resourceGroupId) {
            super(targetDataSource, resourceGroupId);
        }
        
        public void close() throws IOException {
            if (targetDataSource instanceof Closeable)
                ((Closeable) targetDataSource).close();
        }
    }
}
