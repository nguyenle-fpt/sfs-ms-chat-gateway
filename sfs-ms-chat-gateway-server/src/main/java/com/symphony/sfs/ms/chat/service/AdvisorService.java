package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.IMaestroMessage;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.admin.generated.model.AdvisorUpdateRequest;
import com.symphony.sfs.ms.admin.generated.model.AdvisorUpdateRequestItem;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.starter.symphony.tds.TenantDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvisorService implements DatafeedListener {

  private final TenantDetailRepository tenantDetailRepository;

  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final AdminClient adminClient;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  public void onUserUpdated(IMaestroMessage maestroMessage, String podId) {
    if(tenantDetailRepository.findByPodId(podId).isEmpty()){
      LOG.info("Received UPDATE_USER event on a not configured tenant, nothing to do | podId={}", podId);
      return;
    }

    List<AdvisorUpdateRequestItem> items = maestroMessage.getAffectedUsers().stream().map((user) -> iUserToAdvisorUpdateRequestItem(user, podId)).collect(Collectors.toList());
    AdvisorUpdateRequest advisorUpdateRequest = new AdvisorUpdateRequest().advisors(items);

    LOG.info("Received UPDATE_USER event | podId={} affectedUsers={}", podId, items.stream().map(AdvisorUpdateRequestItem::getSymphonyId).collect(Collectors.toList()));
    adminClient.updateAdvisorInfo(advisorUpdateRequest);
  }


  private AdvisorUpdateRequestItem iUserToAdvisorUpdateRequestItem(IUser iUser, String podId) {
    return new AdvisorUpdateRequestItem()
      .symphonyId(iUser.getId().toString())
      .firstName(iUser.getFirstName())
      .lastName(iUser.getSurname())
      .displayName(iUser.getPrettyName())
      .companyName(iUser.getCompany())
      .avatar(iUser.getImageUrl())
      .podId(podId);
  }

}
