package ph.edu.dlsu.lbycpob.profilemanagerapplication.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.edu.dlsu.lbycpob.profilemanagerapplication.model.Profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Profile> findByNameIgnoreCase(String name);

    /** Used by the /lookup endpoint -- returns every name containing the query, alphabetically. */
    List<Profile> findByNameContainingIgnoreCaseOrderByNameAsc(String query);
}
