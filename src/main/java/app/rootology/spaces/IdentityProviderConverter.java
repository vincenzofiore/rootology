package app.rootology.spaces;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the provider by its lowercase name, which is what the check constraint allows. */
@Converter
class IdentityProviderConverter implements AttributeConverter<IdentityProvider, String> {

    @Override
    public String convertToDatabaseColumn(IdentityProvider provider) {
        return provider == null ? null : provider.id();
    }

    @Override
    public IdentityProvider convertToEntityAttribute(String id) {
        return id == null ? null : IdentityProvider.of(id);
    }
}
