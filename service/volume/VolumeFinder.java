package com.serviceco.coex.payment.service.volume;

import java.util.List;

import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.jpa.impl.JPAQueryFactory;

public interface VolumeFinder<T> {
  /**
   * find a collection of T by querying DB entity/view represented by from
   * @param entity
   * @return
   */
  List<T> find(EntityPathBase<T> entity);
  
  default VolumeFinder<T> _new(JPAQueryFactory factory){
   return new GenericVolumeFinder<T>(factory);
  }

}
