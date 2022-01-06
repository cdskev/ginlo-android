// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.log.LogUtil;
import org.greenrobot.greendao.query.CountQuery;
import org.greenrobot.greendao.query.Query;
import org.greenrobot.greendao.query.QueryBuilder;

public class QueryDatabaseTask<T>
        extends ConcurrentTask {
    public static final int MODE_LIST = 0;

    public static final int MODE_UNIQUE = 1;

    public static final int MODE_COUNT = 2;

    private static final int MAX_RETRY = 3;

    private final QueryBuilder<T> queryBuilder;
    private final int mMode;
    private Object mResult;

    public QueryDatabaseTask(QueryBuilder<T> queryBuilder,
                             int mode) {
        super();

        this.queryBuilder = queryBuilder;
        this.mMode = mode;
    }

    @Override
    public void run() {
        super.run();

        switch (mMode) {
            case MODE_LIST: {
                int retry = 0;
                // Retry condition (retry < MAX_RETRY)
                while (true) {
                    try {
                        Query<T> threadQuery = queryBuilder.build().forCurrentThread();
                        mResult = threadQuery.list();
                        complete();
                        break;
                    } catch (IllegalStateException e) {
                        retry++;

                        boolean canRetry = (retry < MAX_RETRY);

                        LogUtil.e(this.getClass().getSimpleName(), "Illegal exception on executing list query. Retry " + canRetry, e);

                        if (!canRetry) throw e;
                    }
                }
                break;
            }
            case MODE_UNIQUE: {
                Query<T> threadQuery = queryBuilder.build().forCurrentThread();
                mResult = threadQuery.unique();
                complete();
                break;
            }
            case MODE_COUNT: {
                CountQuery<T> countQuery = queryBuilder.buildCount().forCurrentThread();
                mResult = countQuery.count();
                complete();
                break;
            }

            default:
                throw new IllegalArgumentException("unknown mMode");
        }
    }

    @Override
    public Object[] getResults() {
        return new Object[]{mResult};
    }
}
