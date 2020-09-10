package com.serviceco.coex.payment.service.volume;

import java.util.List;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.jpa.impl.JPAQueryFactory;

public class GenericVolumeFinder<T> implements VolumeFinder<T> {

  private final JPAQueryFactory factory;

  public GenericVolumeFinder(JPAQueryFactory factory) {
    super();
    this.factory = factory;
  }

  @Override
  public List<T> find(EntityPathBase<T> entity) {
    return factory.select(entity).from(entity).fetch();
  }
  
  public List<T> find(EntityPathBase<T> entity, BooleanExpression whereClause) {
    return factory.select(entity).from(entity).where(whereClause).fetch();
  }
  
  public static <T> GenericVolumeFinder<T> factory(JPAQueryFactory factory){
    return new GenericVolumeFinder<T>(factory);
  }

}
