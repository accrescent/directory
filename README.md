<!--
Copyright 2024 Logan Magee

SPDX-License-Identifier: AGPL-3.0-only
-->

# Accrescent Directory Service

The backend server which powers Accrescent's app directory.

## About

The directory service is Accrescent's server technology which powers Accrescent's app directory
capabilities. This primarily includes serving the app directory API, which allows Accrescent clients
to:

- List apps available for installation in the store
- Retrieve localized app listings for specific apps
- Retrieve app download information based on the requesting device's needs
- Check for app updates
- Check whether an app is compatible with the current Android device

The directory service is still in development but is almost ready for production use. It is intended
to replace Accrescent's current repository model to offer significant improvements in features,
network efficiency, and development simplicity.

If you're interested in helping us reach that goal, consider [donating] to support the project.

[donating]: https://accrescent.app/donate
