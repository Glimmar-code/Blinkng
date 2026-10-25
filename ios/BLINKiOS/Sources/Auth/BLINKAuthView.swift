import SwiftUI

struct BLINKAuthView: View {
    @ObservedObject var auth: BLINKAuthStore

    @State private var email = ""
    @State private var password = ""
    @State private var creatingAccount = false
    @FocusState private var focus: Field?

    private enum Field: Hashable { case email, password }

    private var canSubmit: Bool {
        email.contains("@") && password.count >= 6 && !auth.isBusy
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("BLINK")
                            .font(.system(size: 42, weight: .black))
                            .foregroundStyle(.primary)
                        Text(creatingAccount ? "Create your BLINK account" : "Welcome back")
                            .font(.title2.weight(.semibold))
                        Text("Use the same account as BLINK on the web and Android.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.bottom, 20)

                    VStack(alignment: .leading, spacing: 12) {
                        TextField("Email address", text: $email)
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .focused($focus, equals: .email)
                            .submitLabel(.next)
                            .onSubmit { focus = .password }
                            .accessibilityLabel("Email address")
                            .padding(14)
                            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))

                        SecureField("Password", text: $password)
                            .textContentType(creatingAccount ? .newPassword : .password)
                            .focused($focus, equals: .password)
                            .submitLabel(.go)
                            .onSubmit { submit() }
                            .accessibilityLabel("Password")
                            .padding(14)
                            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))

                        if creatingAccount {
                            Text("Use at least 6 characters. Your BLINK account may require email confirmation.")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }

                    if let message = auth.message {
                        Text(message)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
                            .accessibilityAddTraits(.updatesFrequently)
                    }

                    Button(action: submit) {
                        HStack {
                            Spacer()
                            if auth.isBusy { ProgressView().tint(.white) }
                            Text(creatingAccount ? "Create account" : "Sign in")
                                .fontWeight(.semibold)
                            Spacer()
                        }
                        .padding(15)
                    }
                    .disabled(!canSubmit)
                    .buttonStyle(.borderedProminent)
                    .tint(.primary)
                    .accessibilityIdentifier("blinkAuthSubmit")

                    Button(creatingAccount ? "Already have an account? Sign in" : "New to BLINK? Create account") {
                        creatingAccount.toggle()
                        password = ""
                        auth.message = nil
                    }
                    .font(.subheadline)
                    .disabled(auth.isBusy)

                    if !creatingAccount {
                        Button("Forgot password?") {
                            Task { await auth.sendPasswordReset(email: email) }
                        }
                        .font(.subheadline)
                        .disabled(auth.isBusy || !email.contains("@"))
                    }

                    Divider()
                        .padding(.vertical, 10)

                    Link("Open BLINK on the web", destination: URL(string: "https://www.blink.com.ng/")!)
                        .font(.subheadline)
                }
                .padding(24)
                .frame(maxWidth: 540)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
        }
    }

    private func submit() {
        guard canSubmit else { return }
        focus = nil
        if creatingAccount {
            Task { await auth.signUp(email: email, password: password) }
        } else {
            Task { await auth.signIn(email: email, password: password) }
        }
    }
}
