package com.symphony.sfs.ms.chat.api.util;

import com.symphony.sfs.ms.chat.model.FederatedAccount;
import model.UserInfo;
import org.apache.commons.codec.binary.Base64;

public class SnsMessageUtil {
  public static String getSnsMaestroMessage(String podId, String payload) {
    return "{" +
      "  \"Message\":\"{\\\"payload\\\":\\\"" + Base64.encodeBase64String(payload.getBytes()) + "\\\"}\"," +
      "  \"MessageAttributes\":{" +
      "    \"payloadType\":{" +
      "      \"Type\":\"String\"," +
      "      \"Value\":\"com.symphony.s2.model.chat.MaestroMessage\"" +
      "    }," +
      "    \"podId\":{" +
      "      \"Type\":\"Number\"," +
      "      \"Value\":\"" + podId + "\"" +
      "    }" +
      "  }," +
      "  \"Type\":\"Notification\"" +
      "}";
  }

  public static String getEnvelopeMessage(String payload) {
    return "{" +
      "  \"_type\":\"com.symphony.s2.model.core.Envelope\"," +
      "  \"_version\":\"1.0\"," +
      "  \"createdDate\":\"2020-03-17T12:39:59.117Z\"," +
      "  \"distributionList\":[" +
      "    13469017440257" +
      "  ]," +
      "  \"notificationDate\":\"2020-03-17T12:39:59.407Z\"," +
      "  \"payload\":" + payload + "," +
      "  \"payloadType\":\"com.symphony.s2.model.chat.MaestroMessage.CONNECTION_REQUEST_ALERT\"," +
      "  \"podId\":196," +
      "  \"purgeDate\":\"2027-03-16T12:39:59.117Z\"" +
      "}";
  }

  public static String getAcceptedConnectionRequestMaestroMessage(FederatedAccount requester, FederatedAccount requested) {
    return "{" +
      "  \"_type\":\"com.symphony.s2.model.chat.MaestroMessage\"," +
      "  \"affectedUsers\":[" +
      "    {" +
      "      \"companyName\":\"Symphony\"," +
      "      \"emailAddress\":\"" + requester.getEmailAddress() + "\"," +
      "      \"firstName\":\"" + requester.getFirstName() + "\"," +
      "      \"givenName\":\"" + requester.getFirstName() + "\"," +
      "      \"id\":" + requester.getSymphonyUserId() + "," +
      "      \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "      \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "      \"images\":{" +
      "      }," +
      "      \"location\":\"\"," +
      "      \"prettyName\":\"" + requester.getFirstName() + " " + requester.getLastName() + "\"," +
      "      \"screenName\":\"" + requester.getFirstName() + "\"," +
      "      \"surname\":\"" + requester.getLastName() + "\"," +
      "      \"userType\":\"lc\"," +
      "      \"verifiedForBadge\":true" +
      "    }" +
      "  ]," +
      "  \"copyDisabled\":false," +
      "  \"event\":\"CONNECTION_REQUEST_ALERT\"," +
      "  \"fromPod\":196," +
      "  \"ingestionDate\":1584448898220," +
      "  \"isCopyDisabled\":false," +
      "  \"messageId\":\"jEt58ZHU580+FiQS40XhjH///o8XfHtTbw==\"," +
      "  \"payload\":{" +
      "    \"cargo\":{" +
      "      \"images\":{" +
      "        \"150\":\"../avatars/static/150/default.png\"," +
      "        \"50\":\"../avatars/static/50/default.png\"," +
      "        \"500\":\"../avatars/static/500/default.png\"," +
      "        \"600\":\"../avatars/static/600/default.png\"," +
      "        \"orig\":\"../avatars/static/orig/default.png\"" +
      "      }," +
      "      \"imgUrl\":\"../avatars/static/150/default.png\"," +
      "      \"imgUrlSmall\":\"../avatars/static/50/default.png\"," +
      "      \"initiatorUserId\":13469017440257," +
      "      \"requestCounter\":0," +
      "      \"status\":\"accepted\"," +
      "      \"targetUserId\":13606456395797" +
      "    }," +
      "    \"images\":{" +
      "      \"150\":\"../avatars/static/150/default.png\"," +
      "      \"50\":\"../avatars/static/50/default.png\"," +
      "      \"500\":\"../avatars/static/500/default.png\"," +
      "      \"600\":\"../avatars/static/600/default.png\"," +
      "      \"orig\":\"../avatars/static/orig/default.png\"" +
      "    }," +
      "    \"imgUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imgUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"requestCounter\":0," +
      "    \"status\":\"accepted\"," +
      "    \"targetUserId\":13606456395797," +
      "    \"version\":\"connectionRequestAlertPayload\"" +
      "  }," +
      "  \"podDistribution\":[" +
      "    196," +
      "    198" +
      "  ]," +
      "  \"requestingUser\":{" +
      "    \"company\":\"Symphony\"," +
      "    \"emailAddress\":\"" + requested.getEmailAddress() + "\"," +
      "    \"firstName\":\"" + requested.getFirstName() + "\"," +
      "    \"id\":" + requested.getSymphonyUserId() + "," +
      "    \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"images\":{" +
      "      \"150\":\"../avatars/static/150/default.png\"," +
      "      \"50\":\"../avatars/static/50/default.png\"," +
      "      \"500\":\"../avatars/static/500/default.png\"," +
      "      \"600\":\"../avatars/static/600/default.png\"," +
      "      \"orig\":\"../avatars/static/orig/default.png\"" +
      "    }," +
      "    \"prettyName\":\"" + requested.getFirstName() + " " + requested.getLastName() + "\"," +
      "    \"firstName\":\"" + requested.getLastName() + "\"," +
      "    \"userType\":\"lc\"," +
      "    \"username\":\"" + requested.getSymphonyUsername() + "\"" +
      "  }," +
      "  \"schemaVersion\":1," +
      "  \"semVersion\":\"1.56.0-SNAPSHOT\"," +
      "  \"traceId\":\"EFpFOL\"," +
      "  \"version\":\"MAESTRO\"" +
      "}";
  }

  public static String getConnectionRequestMaestroMessage(UserInfo requester, FederatedAccount requested) {
    return "{" +
      "  \"_type\":\"com.symphony.s2.model.chat.MaestroMessage\"," +
      "  \"affectedUsers\":[" +
      "    {" +
      "      \"companyName\":\"Symphony\"," +
      "      \"emailAddress\":\"" + requester.getEmailAddress() + "\"," +
      "      \"firstName\":\"" + requester.getFirstName() + "\"," +
      "      \"givenName\":\"" + requester.getFirstName() + "\"," +
      "      \"id\":" + requester.getId() + "," +
      "      \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "      \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "      \"images\":{" +
      "      }," +
      "      \"location\":\"\"," +
      "      \"prettyName\":\"" + requester.getFirstName() + " " + requester.getLastName() + "\"," +
      "      \"screenName\":\"" + requester.getFirstName() + "\"," +
      "      \"surname\":\"" + requester.getLastName() + "\"," +
      "      \"userType\":\"lc\"," +
      "      \"verifiedForBadge\":true" +
      "    }" +
      "  ]," +
      "  \"copyDisabled\":false," +
      "  \"event\":\"CONNECTION_REQUEST_ALERT\"," +
      "  \"fromPod\":196," +
      "  \"ingestionDate\":1584448898220," +
      "  \"isCopyDisabled\":false," +
      "  \"messageId\":\"jEt58ZHU580+FiQS40XhjH///o8XfHtTbw==\"," +
      "  \"payload\":{" +
      "    \"cargo\":{" +
      "      \"images\":{" +
      "        \"150\":\"../avatars/static/150/default.png\"," +
      "        \"50\":\"../avatars/static/50/default.png\"," +
      "        \"500\":\"../avatars/static/500/default.png\"," +
      "        \"600\":\"../avatars/static/600/default.png\"," +
      "        \"orig\":\"../avatars/static/orig/default.png\"" +
      "      }," +
      "      \"imgUrl\":\"../avatars/static/150/default.png\"," +
      "      \"imgUrlSmall\":\"../avatars/static/50/default.png\"," +
      "      \"initiatorUserId\":13469017440257," +
      "      \"requestCounter\":0," +
      "      \"status\":\"accepted\"," +
      "      \"targetUserId\":13606456395797" +
      "    }," +
      "    \"images\":{" +
      "      \"150\":\"../avatars/static/150/default.png\"," +
      "      \"50\":\"../avatars/static/50/default.png\"," +
      "      \"500\":\"../avatars/static/500/default.png\"," +
      "      \"600\":\"../avatars/static/600/default.png\"," +
      "      \"orig\":\"../avatars/static/orig/default.png\"" +
      "    }," +
      "    \"imgUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imgUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"requestCounter\":0," +
      "    \"status\":\"pending_incoming\"," +
      "    \"targetUserId\":13606456395797," +
      "    \"version\":\"connectionRequestAlertPayload\"" +
      "  }," +
      "  \"podDistribution\":[" +
      "    196," +
      "    198" +
      "  ]," +
      "  \"requestingUser\":{" +
      "    \"company\":\"Symphony\"," +
      "    \"emailAddress\":\"" + requested.getEmailAddress() + "\"," +
      "    \"firstName\":\"" + requested.getFirstName() + "\"," +
      "    \"id\":" + requested.getSymphonyUserId() + "," +
      "    \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"images\":{" +
      "      \"150\":\"../avatars/static/150/default.png\"," +
      "      \"50\":\"../avatars/static/50/default.png\"," +
      "      \"500\":\"../avatars/static/500/default.png\"," +
      "      \"600\":\"../avatars/static/600/default.png\"," +
      "      \"orig\":\"../avatars/static/orig/default.png\"" +
      "    }," +
      "    \"prettyName\":\"" + requested.getFirstName() + " " + requested.getLastName() + "\"," +
      "    \"firstName\":\"" + requested.getLastName() + "\"," +
      "    \"userType\":\"lc\"," +
      "    \"username\":\"" + requested.getSymphonyUsername() + "\"" +
      "  }," +
      "  \"schemaVersion\":1," +
      "  \"semVersion\":\"1.56.0-SNAPSHOT\"," +
      "  \"traceId\":\"EFpFOL\"," +
      "  \"version\":\"MAESTRO\"" +
      "}";
  }

}
