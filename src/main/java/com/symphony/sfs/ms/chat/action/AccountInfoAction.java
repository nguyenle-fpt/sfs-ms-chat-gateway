package com.symphony.sfs.ms.chat.action;

import com.symphony.security.clientsdk.client.AuthProvider;
import com.symphony.security.clientsdk.client.SymphonyClient;
import com.symphony.security.clientsdk.client.actions.AbstractSymphonyAction;
import com.symphony.security.clientsdk.client.actions.InformationResponse;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.json.internal.json_simple.JSONObject;

import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * Copy / paste of a class from SBE (to avoid introducing dependency). Refine if this should be included as part of standard symphony-client API.
 * It is needed to allow retrieving user id associated to account. User id is needed to identify key material.
 */
@Slf4j
public class AccountInfoAction extends AbstractSymphonyAction {
  private static final String ENDPOINT = "/webcontroller/maestro/Account";
  private AccountContext ctx;
  private String resultString;
  private Map<String, Object> resultMap;

  public AccountInfoAction
    (AuthProvider auth, SymphonyClient client, AccountContext ctx) {
    super(auth, client);
    this.ctx = ctx;
  }

  public String getResponseStringEntity() {
    return this.resultString;
  }

  @Override
  public InformationResponse<Map<String, Object>> serverAction() {
    Response resp = null;
    InformationResponse<Map<String, Object>> ret = new InformationResponse<>();

    try {
      resp = getClient().doGet(getSymphonyAuth(), ENDPOINT, "clienttype", ctx.getClienttype());

      ret.setStatusCode(resp.getStatus());

      if (resp.getStatus() >= 400) {
        ret.setFailed(true);
        String s = resp.readEntity(String.class);
        ret.setJsonBlob(s);
        LOG.info("Unable to get accountInfo for clienttype {} due to {}", ctx.getClienttype());
        return ret;
      }
      this.resultMap = resp.readEntity(Map.class);
      JSONObject jsonEntity = new JSONObject(this.resultMap);
      this.resultString = jsonEntity.toString();

      if (resultMap.size() < 1) {
        ret.setFailed(true);
        String s = resp.readEntity(String.class);
        ret.setJsonBlob(s);
        return ret;
      } else {
        ret.setInfo(resultMap);
        LOGGER.debug("There are total {} attributes found.", resultMap.size());
      }

    } catch (Exception e) {
      LOGGER.error(e.getMessage());
    } finally {
      if (resp != null) {
        resp.close();
      }
    }
    return ret;
  }

  public static class AccountContext {

    private String clienttype;

    public AccountContext(String clienttype) {
      this.clienttype = clienttype;
    }

    public String getClienttype() {
      return clienttype;
    }

    public void setClienttype(String clienttype) {
      this.clienttype = clienttype;
    }
  }


}
