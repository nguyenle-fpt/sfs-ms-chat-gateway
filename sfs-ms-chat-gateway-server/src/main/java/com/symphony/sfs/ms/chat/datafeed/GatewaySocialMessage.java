package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.oss.models.chat.canon.IAttachment;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.util.SpecialCharactersUtils;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

import static org.jsoup.nodes.Entities.escape;

@Getter
@Builder
@ToString
public class GatewaySocialMessage {

  @Setter
  private String textContent;
  private String presentationMLContent;
  private Document parsedPresentationML;
  @NonNull
  private final IUser fromUser;
  @NonNull
  private final List<String> members;
  @Default
  @Setter
  private List<String> toUserIds = new ArrayList<>();
  private final String streamId;
  private final String messageId;
  private final String disclaimer;
  private final List<IAttachment> attachments;
  private final Long timestamp;
  private final boolean chime;
  private final String chatType;
  private final ParentRelationshipType parentRelationshipType;
  private boolean table;
  @Setter
  @Default
  private List<CustomEntity> customEntities = new ArrayList<>();

  public void setPresentationMLContent(String presentationMLContent) {
    this.presentationMLContent = presentationMLContent;
    if (!presentationMLContent.isEmpty()) {
      this.parsedPresentationML = Jsoup.parseBodyFragment(presentationMLContent);
      this.table = !parsedPresentationML.getElementsByTag("table").isEmpty();
    } else {
      this.table = false;
    }
  }

  public String getFromUserId() {
    return this.fromUser.getId().toString();
  }

  public String getMessageForEmp() {
    return escape(SpecialCharactersUtils.unescapeSpecialCharacters(this.textContent));
  }

  public String getDisclaimerForEmp() {
    return escape(this.disclaimer);
  }

  public boolean isRoom() {
    return "CHATROOM".equals(chatType);
  }

  public boolean containsCustomEntityType(String entityType) {
    return customEntities.stream().anyMatch(customEntity -> entityType.equals(customEntity.getType()));
  }

}
