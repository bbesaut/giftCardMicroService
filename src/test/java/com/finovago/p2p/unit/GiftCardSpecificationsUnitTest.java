package com.finovago.p2p.unit;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.repository.specification.GiftCardSpecifications;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

// Exercises each Specification's toPredicate(...) directly against mocked JPA Criteria objects -
// this verifies the actual predicate wiring (which attribute path, which CriteriaBuilder call)
// without needing a real database, unlike GiftCardServiceUnitTest which only checks that the
// service calls the repository with a non-null Specification.
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class GiftCardSpecificationsUnitTest {

    @Mock
    private Root<GiftCard> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Test
    void belongsToMerchant_buildsEqualsPredicateOnMerchantId() {
        Path<Object> merchantPath = mock(Path.class);
        Path<Object> merchantIdPath = mock(Path.class);
        Predicate expected = mock(Predicate.class);

        when(root.<Object>get("merchant")).thenReturn(merchantPath);
        when(merchantPath.<Object>get("id")).thenReturn(merchantIdPath);
        when(cb.equal(merchantIdPath, 42L)).thenReturn(expected);

        Predicate result = GiftCardSpecifications.belongsToMerchant(42L).toPredicate(root, query, cb);

        assertSame(expected, result);
    }

    @Test
    void hasActive_addsNoConstraint_whenNotProvided() {
        // Must not be a null Specification reference: Specification#and(other) rejects null outright,
        // so "no filter" has to be represented as a Specification whose predicate is null instead.
        assertNull(GiftCardSpecifications.hasActive(null).toPredicate(root, query, cb));
    }

    @Test
    void hasActive_buildsEqualsPredicateOnActiveFlag_whenProvided() {
        Path<Object> activePath = mock(Path.class);
        Predicate expected = mock(Predicate.class);

        when(root.<Object>get("active")).thenReturn(activePath);
        when(cb.equal(activePath, true)).thenReturn(expected);

        Predicate result = GiftCardSpecifications.hasActive(true).toPredicate(root, query, cb);

        assertSame(expected, result);
    }

    @Test
    void codeContains_addsNoConstraint_whenNullOrBlank() {
        assertNull(GiftCardSpecifications.codeContains(null).toPredicate(root, query, cb));
        assertNull(GiftCardSpecifications.codeContains("   ").toPredicate(root, query, cb));
    }

    @Test
    void codeContains_buildsCaseInsensitiveLikePredicate_whenProvided() {
        Path<String> codePath = mock(Path.class);
        Expression<String> loweredCode = mock(Expression.class);
        Predicate expected = mock(Predicate.class);

        when(root.<String>get("cardCode")).thenReturn(codePath);
        when(cb.lower(codePath)).thenReturn(loweredCode);
        when(cb.like(loweredCode, "%gc%")).thenReturn(expected);

        Predicate result = GiftCardSpecifications.codeContains("GC").toPredicate(root, query, cb);

        assertSame(expected, result);
    }
}
