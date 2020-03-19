package com.symphony.security.model;

import java.util.List;

/**
 * Created by aaron@symphony.com on 7/9/16.
 */
public class RotationIdInfoList {

  private List<ThreadRotationIdInfo> list;

  public RotationIdInfoList(List<ThreadRotationIdInfo> list) {
    this.list = list;
  }

  public List<ThreadRotationIdInfo> getList() {
    return list;
  }
}
