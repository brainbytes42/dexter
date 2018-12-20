package de.panbytes.dexter.util;

import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;

public interface Named {

    /**
     * Get the Element's name.
     *
     * @return the name.
     */
    RxFieldReadOnly<String> getName();

    /**
     * Set the Element's new name.
     *
     * @param name the new name.
     * @return the object itself (eg. for chaining)
     * @throws NullPointerException if name is null.
     */
    Named setName(String name);

    /**
     * Get the Element's description.
     *
     * @return the description.
     */
    RxFieldReadOnly<String> getDescription();

    /**
     * Set the Element's new description.
     *
     * @param description the new description.
     * @return the object itself (eg. for chaining)
     * @throws NullPointerException if description is null.
     */
    Named setDescription(String description);


    abstract class BaseImpl implements Named {
        private final RxField<String> name;
        private final RxField<String> description;

        protected BaseImpl(String name, String description) {
            this.name = RxField.withInitialValue(name);
            this.description = RxField.withInitialValue(description);
        }

        @Override
        public final RxFieldReadOnly<String> getName() {
            return this.name.toReadOnlyView();
        }

        @Override
        public final Named setName(String name) {
            this.name.setValue(name);
            return this;
        }

        @Override
        public final RxFieldReadOnly<String> getDescription() {
            return this.description.toReadOnlyView();
        }

        @Override
        public final Named setDescription(String description) {
            this.description.setValue(description);
            return this;
        }

        @Override
        public String toString() {
            return "'" + this.name.getValue() + "'_" + this.getClass().getSimpleName() + "@" + Integer.toHexString(
                    this.hashCode());
        }
    }

}
