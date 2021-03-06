package net.corda.core.internal.notary

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.serialize
import net.corda.core.utilities.toBase58String
import java.time.Instant

/** Verifies the signature against this notarisation request. Checks that the signature is issued by the right party. */
fun NotarisationRequest.verifySignature(requestSignature: NotarisationRequestSignature, intendedSigner: Party) {
    try {
        val signature = requestSignature.digitalSignature
        require(intendedSigner.owningKey == signature.by) {
            "Expected a signature by ${intendedSigner.owningKey.toBase58String()}, but received by ${signature.by.toBase58String()}}"
        }

        // TODO: if requestSignature was generated over an old version of NotarisationRequest, we need to be able to
        // reserialize it in that version to get the exact same bytes. Modify the serialization logic once that's
        // available.
        val expectedSignedBytes = this.serialize().bytes
        signature.verify(expectedSignedBytes)
    } catch (e: Exception) {
        val error = NotaryError.RequestSignatureInvalid(e)
        throw NotaryInternalException(error)
    }
}

/**
 * Checks that there are sufficient signatures to satisfy the notary signing requirement and validates the signatures
 * against the given transaction id.
 */
fun NotarisationResponse.validateSignatures(txId: SecureHash, notary: Party) {
    val signingKeys = signatures.map { it.by }
    require(notary.owningKey.isFulfilledBy(signingKeys)) { "Insufficient signatures to fulfill the notary signing requirement for $notary" }
    signatures.forEach { it.verify(txId) }
}

/** Creates a signature over the notarisation request using the legal identity key. */
fun NotarisationRequest.generateSignature(serviceHub: ServiceHub): NotarisationRequestSignature {
    val serializedRequest = this.serialize().bytes
    val signature = with(serviceHub) {
        val myLegalIdentity = myInfo.legalIdentitiesAndCerts.first().owningKey
        keyManagementService.sign(serializedRequest, myLegalIdentity)
    }
    return NotarisationRequestSignature(signature, serviceHub.myInfo.platformVersion)
}

/** Checks if the provided states were used as inputs in the specified transaction. */
fun isConsumedByTheSameTx(txIdHash: SecureHash, consumedStates: Map<StateRef, StateConsumptionDetails>): Boolean {
    val conflicts = consumedStates.filter { (_, cause) ->
        cause.hashOfTransactionId != txIdHash
    }
    return conflicts.isEmpty()
}

/** Returns [NotaryError.TimeWindowInvalid] if [currentTime] is outside the [timeWindow], and *null* otherwise. */
fun validateTimeWindow(currentTime: Instant, timeWindow: TimeWindow?): NotaryError.TimeWindowInvalid? {
    return if (timeWindow != null && currentTime !in timeWindow) {
        NotaryError.TimeWindowInvalid(currentTime, timeWindow)
    } else null
}
