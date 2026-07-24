package ph.edu.dlsu.lbycpob.profilemanagerapplication.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.edu.dlsu.lbycpob.profilemanagerapplication.model.Friend;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendRepository extends JpaRepository<Friend, UUID> {

    List<Friend> findByProfileId(UUID profileId);

    Optional<Friend> findByProfileIdAndFriendId(UUID profileId, UUID friendId);

    void deleteByProfileIdAndFriendId(UUID profileId, UUID friendId);
}
