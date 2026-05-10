package com.sgd_hc.security.config.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Optional;

/**
 * Repositorio base personalizado para forzar el uso de JPQL en búsquedas por ID.
 * Esto asegura que los @Filter de Hibernate se apliquen siempre, ya que
 * Hibernate ignora los filtros en las búsquedas directas (entityManager.find()).
 */
public class FilteredJpaRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

    private final EntityManager entityManager;
    private final JpaEntityInformation<T, ?> entityInformation;

    public FilteredJpaRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.entityInformation = entityInformation;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<T> findById(ID id) {
        Assert.notNull(id, "The given id must not be null!");
        
        // Al usar JPQL, Hibernate aplica automáticamente el @Filter si la entidad lo tiene
        String jpql = String.format("SELECT e FROM %s e WHERE e.%s = :id",
                entityInformation.getEntityName(),
                entityInformation.getIdAttribute().getName());

        try {
            T entity = entityManager.createQuery(jpql, entityInformation.getJavaType())
                    .setParameter("id", id)
                    .getSingleResult();
            return Optional.of(entity);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(ID id) {
        Assert.notNull(id, "The given id must not be null!");
        
        String jpql = String.format("SELECT COUNT(e) FROM %s e WHERE e.%s = :id",
                entityInformation.getEntityName(),
                entityInformation.getIdAttribute().getName());

        Long count = entityManager.createQuery(jpql, Long.class)
                .setParameter("id", id)
                .getSingleResult();
        return count > 0;
    }

    @Override
    @Transactional
    public void deleteById(ID id) {
        Assert.notNull(id, "The given id must not be null!");
        T entity = findById(id).orElseThrow(() -> 
            new org.springframework.dao.EmptyResultDataAccessException("Recurso no encontrado con id: " + id + " (o no pertenece a tu clínica).", 1)
        );
        delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public T getReferenceById(ID id) {
        Assert.notNull(id, "The given id must not be null!");
        return findById(id).orElseThrow(() -> 
            new jakarta.persistence.EntityNotFoundException("No se encontró " + entityInformation.getEntityName() + " con id " + id)
        );
    }

    @Override
    @Deprecated
    @Transactional(readOnly = true)
    public T getById(ID id) {
        return getReferenceById(id);
    }

    @Override
    @Deprecated
    @Transactional(readOnly = true)
    public T getOne(ID id) {
        return getReferenceById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<T> findAllById(Iterable<ID> ids) {
        Assert.notNull(ids, "The given Iterable of ids must not be null!");
        if (!ids.iterator().hasNext()) {
            return java.util.Collections.emptyList();
        }
        
        java.util.List<ID> idList = new java.util.ArrayList<>();
        ids.forEach(idList::add);

        String jpql = String.format("SELECT e FROM %s e WHERE e.%s IN :ids",
                entityInformation.getEntityName(),
                entityInformation.getIdAttribute().getName());

        return entityManager.createQuery(jpql, entityInformation.getJavaType())
                .setParameter("ids", idList)
                .getResultList();
    }
}
