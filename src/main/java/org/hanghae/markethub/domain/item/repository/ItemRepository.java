package org.hanghae.markethub.domain.item.repository;

import org.hanghae.markethub.domain.item.entity.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
	@Query("SELECT i FROM Item i WHERE i.id = :itemId AND i.status = 'EXIST'")
	Optional<Item> findById(@Param("itemId")Long itemId);

	@Query("SELECT p FROM Item p WHERE p.category = :category AND p.store.id = :storeId AND p.status = 'EXIST'")
	List<Item> findByCategoryAndStoreId(@Param("category") String category, @Param("storeId") Long storeId);

	@Query("SELECT i FROM Item i WHERE i.status = 'EXIST'")
	Page<Item> findAll(Pageable pageable);

	@Query("SELECT DISTINCT i FROM Item i LEFT JOIN Picture p ON i.id = p.item.id WHERE i.status = 'EXIST'")
	List<Item> findAllNotPageable();

	@Query("SELECT i FROM Item i WHERE i.user.id = :userId AND i.status = 'EXIST'")
	List<Item> findByUserId(@Param("userId") Long userId);

}
